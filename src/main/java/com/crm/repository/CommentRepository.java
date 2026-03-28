package com.crm.repository;

import com.crm.model.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Репозиторий комментариев.
 *
 * Нюанс: комментарии обычно запрашиваются в контексте сделки.
 * ON DELETE CASCADE в DDL Liquibase обеспечивает автоматическое удаление
 * комментариев при удалении Deal на уровне БД (дополнительно к cascade JPA).
 */
public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByDealId(Long dealId);

    List<Comment> findByDealIdOrderByCreatedAtDesc(Long dealId);

    long countByDealId(Long dealId);
}
