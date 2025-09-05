package org.gridsuite.shortcircuit.server.utils;

import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.extensions.BusbarSectionPositionAdder;

import java.util.UUID;

public final class NetworkUtil {

    private NetworkUtil() { }

    //Create a network with fictitious switch and different switch kind
    public static Network createSwitchNetwork(UUID uuid, NetworkFactory networkFactory) {
        Network network = networkFactory.createNetwork(uuid.toString(), "test");

        Substation s1 = network.newSubstation()
                .setId("s1")
                .setName("s1")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl1 = createVoltageLevel(s1, "vl1", "vl1", TopologyKind.NODE_BREAKER, 400.0);
        vl1.getNodeBreakerView().newBusbarSection()
                .setId("b1")
                .setName("b1")
                .setNode(0)
                .add();
        vl1.getNodeBreakerView().getBusbarSection("b1")
                .newExtension(BusbarSectionPositionAdder.class)
                .withBusbarIndex(1)
                .withSectionIndex(1)
                .add();
        VoltageLevel vl2 = createVoltageLevel(s1, "vl2", "vl2", TopologyKind.NODE_BREAKER, 400.0);
        vl2.getNodeBreakerView().newBusbarSection()
                .setId("b2")
                .setName("b2")
                .setNode(0)
                .add();
        vl2.getNodeBreakerView().getBusbarSection("b2")
                .newExtension(BusbarSectionPositionAdder.class)
                .withBusbarIndex(1)
                .withSectionIndex(1)
                .add();

        createSwitch(vl1, "b4", "b4", SwitchKind.DISCONNECTOR, false, false, false, 0, 1);
        createSwitch(vl1, "br11", "br11", SwitchKind.BREAKER, false, false, false, 1, 2);
        createSwitch(vl2, "b5", "b5", SwitchKind.DISCONNECTOR, false, false, false, 0, 1);
        createSwitch(vl2, "br21", "br21", SwitchKind.BREAKER, false, false, true, 1, 2);

        network.newLine()
                .setId("line2")
                .setName("line2")
                .setVoltageLevel1("vl1")
                .setVoltageLevel2("vl2")
                .setR(0.1)
                .setX(10.0)
                .setG1(0.0)
                .setG2(0.0)
                .setB1(0.0)
                .setB2(0.0)
                .setNode1(2)
                .setNode2(2)
                .add();

        network.getVariantManager().setWorkingVariant(VariantManagerConstants.INITIAL_VARIANT_ID);
        return network;
    }

    public static VoltageLevel createVoltageLevel(Substation s, String id, String name,
                                                   TopologyKind topology, double vNom) {
        return s.newVoltageLevel()
                .setId(id)
                .setName(name)
                .setTopologyKind(topology)
                .setNominalV(vNom)
                .add();
    }

    @SuppressWarnings("SameParameterValue")
    public static void createSwitch(VoltageLevel vl, String id, String name, SwitchKind kind, boolean retained, boolean open, boolean fictitious, int node1, int node2) {
        vl.getNodeBreakerView().newSwitch()
                .setId(id)
                .setName(name)
                .setKind(kind)
                .setRetained(retained)
                .setOpen(open)
                .setFictitious(fictitious)
                .setNode1(node1)
                .setNode2(node2)
                .add();
    }
}
