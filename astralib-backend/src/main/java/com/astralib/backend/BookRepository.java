package com.astralib.backend;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookRepository extends JpaRepository<Book, Long> {
    Optional<Book> findByIsbn(String isbn);
    Optional<Book> findByResourceId(String resourceId);
    Optional<Book> findByOpenLibraryKey(String openLibraryKey);

    /**
     * Books that have a stored file, i.e. the ones a reader can actually
     * open. The library catalogue uses this so it only lists real
     * holdings and never metadata-only dataset records.
     */
    @Query("select distinct b from Book b join b.digitalResources r "
            + "where r.filePath is not null and trim(r.filePath) <> ''")
    List<Book> findWithStoredResource();

    /**
     * Everything the library actually advertises: books with a readable
     * file, plus curated catalogue entries that are described and
     * levelled but not yet available to read. Dataset-only records stay
     * out.
     */
    @Query("select distinct b from Book b left join fetch b.digitalResources r "
            + "where b.catalogueEntry = true "
            + "or (r.filePath is not null and trim(r.filePath) <> '')")
    List<Book> findCatalogueBooks();

    /**
     * Books with no stored file and no resource identifier yet: records
     * that a later upload may safely attach itself to.
     */
    @Query("select b from Book b "
            + "where (b.resourceId is null or b.resourceId = '') "
            + "and not exists (select r from DigitalResource r where r.book = b)")
    List<Book> findWithoutStoredResource();

    @Query("select distinct b from Book b left join b.author a left join b.category c "
            + "where (:query = '' or lower(b.title) like lower(concat('%', :query, '%')) "
            + "or lower(coalesce(b.description, '')) like lower(concat('%', :query, '%')) "
            + "or lower(coalesce(a.name, '')) like lower(concat('%', :query, '%')) "
            + "or lower(coalesce(c.name, '')) like lower(concat('%', :query, '%')) "
            + "or exists (select s from b.subjects s where lower(s) like lower(concat('%', :query, '%')))) "
            + "and (:category is null or lower(c.name) like lower(concat('%', :category, '%'))) "
            + "and (:author is null or lower(a.name) like lower(concat('%', :author, '%'))) "
            + "and (:year is null or b.publicationYear = :year) "
            + "and (:resourceType is null or lower(b.resourceType) = lower(:resourceType))")
    Page<Book> search(@Param("query") String query,
                      @Param("category") String category,
                      @Param("author") String author,
                      @Param("year") Integer year,
                      @Param("resourceType") String resourceType,
                      Pageable pageable);
}
