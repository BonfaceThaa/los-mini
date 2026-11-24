package com.credvenn.stmt.analyzer.tests;

import org.junit.jupiter.api.Test;

class CoreSmokeRunnerTest {
    @Test
    void coreIsAvailableWithoutSpringOrDatabase() throws Exception {
        CoreSmokeRunner.main(new String[0]);
    }
}
