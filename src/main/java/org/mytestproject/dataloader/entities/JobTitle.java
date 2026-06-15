package org.mytestproject.dataloader.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "job_title", uniqueConstraints = @UniqueConstraint(columnNames = "title"))
public class JobTitle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    public JobTitle(String title) {
        this.title = title;
    }
}
