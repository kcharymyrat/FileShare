package fileshare;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
public class DirectoryService {

    @Value("${uploads.dir}")
    private String uploadsDirectoryPath;


    public File createUploadsDirectory(){
        // create a directory
        File uploadsDir = new File(getUploadsDirectoryPath());

        if (!uploadsDir.exists()) {
            boolean created = uploadsDir.mkdirs();
            if (created) {
                System.out.println("Uploads directory created successfully at: " + uploadsDir.getAbsolutePath());
            } else {
                System.err.println("Failed to create uploads directory at: " + uploadsDir.getAbsolutePath());
            }
        } else {
            System.out.println("Uploads directory already exists at: " + uploadsDir.getAbsolutePath());
        }

        System.out.printf("uploadsDir = %s\n", uploadsDir);
        return uploadsDir;
    }


    public String getUploadsDirectoryPath() {
        return uploadsDirectoryPath;
    }
}
