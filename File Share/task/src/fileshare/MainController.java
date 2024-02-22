package fileshare;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@RestController
public class MainController {

    private final DirectoryService dirService;
    private final FileDetailRepository repository;

    @Value("${server.port}")
    private String serverPort;

    @Value("${server.host}")
    private String serverHost;

    private final List<String> ALLOWED_MEDIA_TYPES = Arrays.asList(
            "text/plain",
            "image/jpeg",
            "image/png"
    );


    public MainController(@Autowired DirectoryService dirService, @Autowired FileDetailRepository repository) {
        this.dirService = dirService;
        this.repository = repository;
    }

    @PostMapping("/api/v1/upload")
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file
    ) {
        long maxStorageSpace = 200 * 1000;  // 200KB
        long maxFileSize = 50 * 1024;  // 50KB

        // Check if file exist first by hash
        // Get the hash value of the file
        String hashValue = createHashValue(file);
        List<FileDetailEntity> fDList = repository.findAllByHashValue(hashValue);
        boolean isFile = !fDList.isEmpty();


        // Check if file is empty
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("File is empty");
        }

        // get the uploads File
        File uploadsDir = dirService.createUploadsDirectory();

        try {
            // Get the size of the folder uploads
            File[] subFiles = uploadsDir.listFiles();
            long totalStorage = 0;
            if (subFiles != null) {
                totalStorage = subFiles.length;
                for (File subFile : subFiles) {
                    System.out.printf("file = %s, size = %d\n", subFile.getName(), subFile.length());
                    totalStorage += subFile.length();
                }
            }
            long spaceLeft = maxStorageSpace - totalStorage;
            System.out.printf("newFile = %s, size = %d\n", file.getName(), file.getSize());
            System.out.printf("spaceLeft = %d, newFileSize %d\n", spaceLeft, file.getSize());
            if (spaceLeft < 0) {
                return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
            }

            if (!isFile) {
                if (file.getSize() > spaceLeft || file.getSize() > maxFileSize) {
                    return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
                }
            }

            // Get the Content type
            String contentType = file.getContentType();
            if (contentType == null || !ALLOWED_MEDIA_TYPES.contains(contentType)) {
                return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build();
            }

            // Get the Bytes
            switch (contentType) {
                case ("image/png"):
                    if (!isPng(file)) {
                        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build();
                    }
                    break;
                case ("image/jpeg"):
                    if (!isJpeg(file)) {
                        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build();
                    }
                    break;
                case ("text/plain"):
                    if (!isUtf8Decoded(file)) {
                        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build();
                    }
                    break;
                default:
                    return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build();
            }

            // Check if the hash value exist in db
            String realFileName = "";
            if (!fDList.isEmpty()) {
                if (fDList.get(0).getRealFileName() != null) {
                    realFileName = fDList.get(0).getRealFileName();
                }
            }

            FileDetailEntity fileDetailEntity = new FileDetailEntity();
            fileDetailEntity.setHashValue(hashValue);
            fileDetailEntity.setReceivedName(file.getOriginalFilename());
            repository.save(fileDetailEntity);
            String idStr = String.format("%d", fileDetailEntity.getId());

            if (realFileName.isEmpty()) {
                realFileName = idStr + "-" + file.getOriginalFilename();
            }
            System.out.printf("id = %s\n", idStr);
            System.out.printf("realFileName = %s\n", realFileName);

            String newFileName = realFileName;
            Path path = Path.of(uploadsDir.getPath(), realFileName);
            if (!Files.exists(path)) {
                file.transferTo(path);
            }

            String encodedFileName = URLEncoder.encode(Objects.requireNonNull(newFileName), StandardCharsets.UTF_8);

            // Save it in DB
            fileDetailEntity.setFilename(file.getOriginalFilename());
            fileDetailEntity.setEncodedFilename(encodedFileName);
            fileDetailEntity.setRealFileName(realFileName);
            repository.save(fileDetailEntity);
            System.out.printf("id = %s\n", fileDetailEntity.getId());

            String fileUrl = String.format("http://%s:%s/api/v1/download/%d", serverHost, serverPort, fileDetailEntity.getId());
            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(new URI(fileUrl));
            headers.setContentType(MediaType.valueOf(Objects.requireNonNull(file.getContentType())));

            System.out.printf("\n\n\nfileName = %s\n", file.getOriginalFilename());
            System.out.printf("fileUrl = %s\n", fileUrl);

            return new ResponseEntity<>(headers, HttpStatus.CREATED);
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException(e);
        }

    }


    @GetMapping("/api/v1/info")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<?> getFileInfo() {
        File uploadsDir = dirService.createUploadsDirectory();
        File[] subFiles = uploadsDir.listFiles();

        if (subFiles != null) {
            int totalFiles = subFiles.length;
            long totalBytes = 0;
            for (File subFile : subFiles) {
                totalBytes += subFile.length();
            }

            return ResponseEntity.ok(new FileInfoDTO(totalFiles, totalBytes));

        }

        return ResponseEntity.ok().build();
    }


    @GetMapping("api/v1/download/{strId}")
    public ResponseEntity<?> download(
            @PathVariable String strId
    ) {
        long id;
        try {
            id = Long.parseLong(strId);
            // Do something with the number
        } catch (NumberFormatException e) {
            // Handle the case where the string is not a valid long
            return ResponseEntity.notFound().build();
        }

        Optional<FileDetailEntity> fileDetailEntityOptional = repository.findById(id);
        if (fileDetailEntityOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        FileDetailEntity fileDetailEntity = fileDetailEntityOptional.get();
        System.out.printf("in download: fileDetailEntity = %s\n\n\n", fileDetailEntity);
        String encodedFilename = fileDetailEntity.getEncodedFilename();

        // Decode filename
        String decodedFilename = URLDecoder.decode(encodedFilename, StandardCharsets.UTF_8);

        // Get file and it's path
        String realFileName = fileDetailEntity.getRealFileName();
        File uploadsDir = dirService.createUploadsDirectory();
        Path path = Path.of(uploadsDir.getPath(), realFileName);

        if (uploadsDir.exists()) {
            // Read the file content into a byte array
            try {
                System.out.printf("in download: uploadsDir = %s\n\n\n", uploadsDir);
                byte[] fileContent = Files.readAllBytes(path);

                // Set Content-Disposition header
                String contentDispositionValue = String.format("attachment; filename=\"%s\"", fileDetailEntity.getReceivedName());

                System.out.printf("Files.probeContentType(path) = %s\n", Files.probeContentType(path));
                // Set the response headers
                HttpHeaders headers = new HttpHeaders();
                headers.set(HttpHeaders.CONTENT_DISPOSITION, contentDispositionValue);
                headers.setContentType(MediaType.parseMediaType(Files.probeContentType(path)));

                // Return the file content with status code 200 OK
                return new ResponseEntity<>(fileContent, headers, HttpStatus.OK);
            } catch (Exception e) {
                return ResponseEntity.notFound().build();
            }
        } else {
            // Return status code 404 NOT FOUND if the file does not exist
            return ResponseEntity.notFound().build();
        }

//        // Create a Resource from the path
//        Resource resource = new PathResource(path);
//
//        // Check if the resource exists and is readable
//        if (!resource.exists() || !resource.isReadable()) {
//            return ResponseEntity.notFound().build();
//        }
//
//        // Creating the Streaming Response
//        StreamingResponseBody responseBody = outputStream -> {
//            try (InputStream inputStream = resource.getInputStream()) {
//                byte[] buffer = new byte[4096];
//                int bytesRead;
//                while ((bytesRead = inputStream.read(buffer)) != -1) {
//                    outputStream.write(buffer, 0, bytesRead);
//                }
//            } catch (IOException e) {
//                throw new RuntimeException(e);
//            }
//        };
//
//        // Setting the response header
//        HttpHeaders headers = new HttpHeaders();
//        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
//        headers.setContentDispositionFormData("attachment", resource.getFilename());
//
//        // Assembling response
//        return ResponseEntity
//                .ok()
//                .headers(headers)
//                .body(responseBody);
    }

    private String createHashValue(MultipartFile file) {
        try {
            byte[] fileBytes = file.getBytes();
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(fileBytes);
            byte[] digest = md.digest();
            String hexHashStr = bytesToHex(fileBytes);
            return hexHashStr;
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private boolean isPng(MultipartFile file) {
        final byte[] pngStartBytes = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        try {
            byte[] fileBytes = file.getBytes();
            byte[] headerBytes = new byte[pngStartBytes.length];
            System.arraycopy(fileBytes, 0, headerBytes, 0, pngStartBytes.length);
            return Arrays.equals(headerBytes, pngStartBytes);
        } catch (CharacterCodingException e) {
            return false;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isJpeg(MultipartFile file) {
        final byte[] jpegFirstBytes = {(byte) 0xFF, (byte) 0xD8};
        final byte[] jpegLastBytes = {(byte) 0xFF, (byte) 0xD9};
        try {
            byte[] fileBytes = file.getBytes();
            byte[] headerBytes = new byte[jpegFirstBytes.length];
            byte[] tailBytes = new byte[jpegFirstBytes.length];
            for (int i = 0; i < 2; i++) {
                headerBytes[i] = fileBytes[i];
                tailBytes[tailBytes.length - 1 - i] = fileBytes[fileBytes.length - 1 - i];
            }
            return Arrays.equals(headerBytes, jpegFirstBytes) && Arrays.equals(tailBytes, jpegLastBytes);
        } catch (CharacterCodingException e) {
            return false;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isUtf8Decoded(MultipartFile file) {
        try {
            byte[] contents = file.getBytes();
            CharsetDecoder utf8Decoder = StandardCharsets.UTF_8.newDecoder();
            utf8Decoder.reset();
            utf8Decoder.decode(ByteBuffer.wrap(contents));
            // no exception - the byte array contains only valid UTF-8 byte sequences
            return true;
        } catch (CharacterCodingException e) {
            return false;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
