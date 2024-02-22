package fileshare;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FileInfoDTO(
    @JsonProperty("total_files") int totalFiles,
    @JsonProperty("total_bytes") long totalBytes
){}

