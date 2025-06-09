package com.jaoow.helmetstore.dto.inventory;

import com.jaoow.helmetstore.dto.info.PublicProductStockDto;
import lombok.*;

import java.util.List;

@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@RequiredArgsConstructor
@AllArgsConstructor
public class ShareLinkStoreViewDTO extends ShareLinkDTO {
    public List<PublicProductStockDto> products;
}
