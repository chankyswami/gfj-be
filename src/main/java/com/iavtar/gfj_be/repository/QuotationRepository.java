package com.iavtar.gfj_be.repository;

import com.iavtar.gfj_be.entity.Quotation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuotationRepository extends JpaRepository<Quotation, Long> {

    Iterable<Long> id(Long id);

    Page<Quotation> findAllByClientId(Long clientId, Pageable pageable);

    Page<Quotation> findAllByAgentId(Long agentId, Pageable pageable);

    Page<Quotation> findAllByClientIdAndAgentId(Long clientId, Long agentId, Pageable pageable);

    Optional<Quotation> findByQuotationId(String quotationId);

    List<Quotation> findAllByShippingId(String shippingId);
}
