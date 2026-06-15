package org.mytestproject.dataloader.repositories;

import org.mytestproject.dataloader.entities.JobTitle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface JobTitleRepository extends JpaRepository<JobTitle, Long> {

    Optional<JobTitle> findByTitle(String title);
}
