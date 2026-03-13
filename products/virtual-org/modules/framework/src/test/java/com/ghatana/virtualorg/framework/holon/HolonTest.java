package com.ghatana.virtualorg.framework.holon;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the Holonic Architecture implementation.
 */
@DisplayName("Holonic Architecture Tests")
class HolonTest extends EventloopTestBase {

    @Test
    @DisplayName("Should create holon with unique identity")
    void shouldCreateHolonWithIdentity() {
        AbstractHolon company = new TestHolon("company-1", "Company", Holon.HolonType.ORGANIZATION);

        assertThat(company.getId()).isEqualTo("company-1");
        assertThat(company.getName()).isEqualTo("Company");
        assertThat(company.getType()).isEqualTo(Holon.HolonType.ORGANIZATION);
    }

    @Test
    @DisplayName("Should add and retrieve child holons")
    void shouldAddAndRetrieveChildHolons() {
        AbstractHolon company = new TestHolon("company", "Company", Holon.HolonType.ORGANIZATION);
        AbstractHolon engineering = new TestHolon("engineering", "Engineering", Holon.HolonType.DEPARTMENT);
        AbstractHolon team = new TestHolon("team-alpha", "Team Alpha", Holon.HolonType.TEAM);

        runPromise(() -> company.addChild(engineering));
        runPromise(() -> engineering.addChild(team));

        assertThat(company.getChildren()).hasSize(1);
        assertThat(company.getChildren().get(0).getName()).isEqualTo("Engineering");

        assertThat(engineering.getChildren()).hasSize(1);
        assertThat(engineering.getChildren().get(0).getName()).isEqualTo("Team Alpha");
    }

    @Test
    @DisplayName("Should maintain parent-child bidirectional relationship")
    void shouldMaintainParentChildRelationship() {
        AbstractHolon company = new TestHolon("company", "Company", Holon.HolonType.ORGANIZATION);
        AbstractHolon engineering = new TestHolon("engineering", "Engineering", Holon.HolonType.DEPARTMENT);

        runPromise(() -> company.addChild(engineering));

        assertThat(engineering.getParent()).isPresent();
        assertThat(engineering.getParent().get()).isEqualTo(company);
        assertThat(engineering.isRoot()).isFalse();
        assertThat(company.isRoot()).isTrue();
    }

    @Test
    @DisplayName("Should find holon by ID recursively")
    void shouldFindHolonByIdRecursively() {
        AbstractHolon company = new TestHolon("company", "Company", Holon.HolonType.ORGANIZATION);
        AbstractHolon engineering = new TestHolon("engineering", "Engineering", Holon.HolonType.DEPARTMENT);
        AbstractHolon team = new TestHolon("team-alpha", "Team Alpha", Holon.HolonType.TEAM);

        runPromise(() -> company.addChild(engineering));
        runPromise(() -> engineering.addChild(team));

        Optional<Holon> found = company.findById("team-alpha");
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Team Alpha");
    }

    @Test
    @DisplayName("Should get all descendant holons")
    void shouldGetAllDescendants() {
        AbstractHolon company = new TestHolon("company", "Company", Holon.HolonType.ORGANIZATION);
        AbstractHolon engineering = new TestHolon("engineering", "Engineering", Holon.HolonType.DEPARTMENT);
        AbstractHolon team = new TestHolon("team-alpha", "Team Alpha", Holon.HolonType.TEAM);

        runPromise(() -> company.addChild(engineering));
        runPromise(() -> engineering.addChild(team));

        List<Holon> descendants = company.getAllDescendants();
        assertThat(descendants).hasSize(2);
    }

    @Test
    @DisplayName("Should remove child holon")
    void shouldRemoveChildHolon() {
        AbstractHolon company = new TestHolon("company", "Company", Holon.HolonType.ORGANIZATION);
        AbstractHolon engineering = new TestHolon("engineering", "Engineering", Holon.HolonType.DEPARTMENT);

        runPromise(() -> company.addChild(engineering));
        assertThat(company.getChildren()).hasSize(1);

        runPromise(() -> company.removeChild("engineering"));
        assertThat(company.getChildren()).isEmpty();
    }

    @Test
    @DisplayName("Should calculate depth in hierarchy")
    void shouldCalculateDepth() {
        AbstractHolon company = new TestHolon("company", "Company", Holon.HolonType.ORGANIZATION);
        AbstractHolon engineering = new TestHolon("engineering", "Engineering", Holon.HolonType.DEPARTMENT);
        AbstractHolon team = new TestHolon("team", "Team Alpha", Holon.HolonType.TEAM);

        runPromise(() -> company.addChild(engineering));
        runPromise(() -> engineering.addChild(team));

        assertThat(company.getDepth()).isEqualTo(0);
        assertThat(engineering.getDepth()).isEqualTo(1);
        assertThat(team.getDepth()).isEqualTo(2);
    }

    /**
     * Test implementation of AbstractHolon.
     */
    private static class TestHolon extends AbstractHolon {
        TestHolon(String id, String name, Holon.HolonType type) {
            super(id, name, type);
        }
    }
}
