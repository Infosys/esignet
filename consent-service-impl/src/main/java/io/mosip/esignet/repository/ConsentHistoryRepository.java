package io.mosip.esignet.repository;

import io.mosip.esignet.entity.ConsentHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentHistoryRepository extends JpaRepository<ConsentHistory, String>{
}
