package com.jaoow.helmetstore.repository;

import com.jaoow.helmetstore.model.StoreInfo;
import com.jaoow.helmetstore.model.inventory.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StoreInfoRepository extends JpaRepository<StoreInfo, Long> {
    Optional<StoreInfo> findByInventory(Inventory inventory);
}
