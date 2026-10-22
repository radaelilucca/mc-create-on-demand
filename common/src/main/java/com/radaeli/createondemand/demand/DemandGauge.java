package com.radaeli.createondemand.demand;

import java.util.UUID;

public interface DemandGauge {
    UUID demandId();
    DemandEngine.Mode demandMode();
    void demandMode(DemandEngine.Mode mode);
}
