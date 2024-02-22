package fileshare;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FileDetailRepository extends CrudRepository<FileDetailEntity, Long> {
    Optional<FileDetailEntity> findById(Long id);
    List<FileDetailEntity> findAllByHashValue(String hashValue);
}
