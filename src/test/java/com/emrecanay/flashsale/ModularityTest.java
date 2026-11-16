package com.emrecanay.flashsale;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTest {

    /** Fails the build on dependency cycles between the top-level packages (catalog, order, shared). */
    @Test
    void modulesHaveNoCyclesAndOnlyUseEachOthersPublicApi() {
        ApplicationModules.of(FlashSaleApplication.class).verify();
    }
}
