package fileshare;

import jakarta.persistence.*;

@Entity
@Table(name="file_details")
public class FileDetailEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "received_name")
    private String receivedName;

    @Column(name = "file_name")
    private String filename;

    @Column(name = "encoded_filename")
    private String encodedFilename;

    @Column(name = "hash_value", length = 200000)
    private String hashValue;

    @Column(name = "real_file_name")
    private String realFileName;

    @Override
    public String toString() {
        return "FileDetailEntity{" +
                "id=" + id +
                ", receivedName='" + receivedName + '\'' +
                ", filename='" + filename + '\'' +
                ", encodedFilename='" + encodedFilename + '\'' +
                ", hashValue='" + hashValue.substring(0, 7) + '\'' +
                ", realFileName='" + realFileName + '\'' +
                '}';
    }

    public FileDetailEntity() {
    }

    public FileDetailEntity(String fileName, String encodedFilename) {
        this.filename = fileName;
        this.encodedFilename = encodedFilename;
    }

    public Long getId() {
        return id;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getEncodedFilename() {
        return encodedFilename;
    }

    public void setEncodedFilename(String encodedFilename) {
        this.encodedFilename = encodedFilename;
    }

    public String getReceivedName() {
        return receivedName;
    }

    public void setReceivedName(String receivedName) {
        this.receivedName = receivedName;
    }

    public String getHashValue() {
        return hashValue;
    }

    public void setHashValue(String hashValue) {
        this.hashValue = hashValue;
    }

    public String getRealFileName() {
        return realFileName;
    }

    public void setRealFileName(String realFileName) {
        this.realFileName = realFileName;
    }
}
