package org.mytestproject.dataloader.repositories;

import org.mytestproject.dataloader.entities.SkipStatus;
import org.mytestproject.dataloader.entities.SkippedRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface SkippedRecordRepository extends JpaRepository<SkippedRecord, Long> {

    Optional<SkippedRecord> findByLoadIdAndRecordId(String loadId, String recordId);

    List<SkippedRecord> findByLoadId(String loadId);

    List<SkippedRecord> findByLoadIdAndStatus(String loadId, SkipStatus status);
}
