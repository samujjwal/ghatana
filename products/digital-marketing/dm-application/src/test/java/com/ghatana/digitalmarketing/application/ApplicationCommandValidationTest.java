package com.ghatana.digitalmarketing.application;

import com.ghatana.digitalmarketing.application.brand.BrandCatalogService;
import com.ghatana.digitalmarketing.application.campaign.CampaignService;
import com.ghatana.digitalmarketing.application.contact.ContactService;
import com.ghatana.digitalmarketing.application.identity.ContactIdentityService;
import com.ghatana.digitalmarketing.application.workspace.WorkspaceService;
import com.ghatana.digitalmarketing.domain.campaign.CampaignType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DisplayName("DM Application Command Validation")
class ApplicationCommandValidationTest {

    @Test
    @DisplayName("brand commands validate required fields and copy collections")
    void shouldValidateBrandCommands() {
        BrandCatalogService.UpsertBrandProfileCommand profileCommand = new BrandCatalogService.UpsertBrandProfileCommand(
            "Brand",
            "Tone",
            null,
            null
        );

        assertThat(profileCommand.brandColors()).isEmpty();
        assertThat(profileCommand.targetGeographies()).isEmpty();

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new BrandCatalogService.UpsertBrandProfileCommand(" ", "Tone", List.of(), List.of()));

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new BrandCatalogService.CreateOfferCommand(" ", "desc", new BigDecimal("1.00"), "USD"));

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new BrandCatalogService.CreateOfferCommand("Offer", "desc", new BigDecimal("-1.00"), "USD"));

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new BrandCatalogService.CreateOfferCommand("Offer", "desc", new BigDecimal("1.00"), " "));

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new BrandCatalogService.UpdateOfferCommand(" ", "desc", new BigDecimal("1.00"), "USD", true));

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new BrandCatalogService.UpdateOfferCommand("Offer", "desc", null, "USD", true));

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new BrandCatalogService.UpdateOfferCommand("Offer", "desc", new BigDecimal("1.00"), "", true));
    }

    @Test
    @DisplayName("workspace and contact commands reject blank required values")
    void shouldValidateWorkspaceAndContactCommands() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new WorkspaceService.CreateWorkspaceCommand("", "desc"));

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new ContactService.RegisterContactCommand(" ", "User"));
    }

    @Test
    @DisplayName("campaign command validates null and blank values")
    void shouldValidateCampaignCommand() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> new CampaignService.CreateCampaignCommand(
                null,
                CampaignType.EMAIL,
                null,
                null,
                null,
                null,
                null,
                null
            ));

        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> new CampaignService.CreateCampaignCommand(
                "Launch",
                null,
                null,
                null,
                null,
                null,
                null,
                null
            ));

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new CampaignService.CreateCampaignCommand(
                " ",
                CampaignType.EMAIL,
                null,
                null,
                null,
                null,
                null,
                null
            ));
    }

    @Test
    @DisplayName("identity command normalizes null attributes")
    void shouldNormalizeIdentityAttributes() {
        ContactIdentityService.UpsertIdentityCommand command =
            new ContactIdentityService.UpsertIdentityCommand("+1555", "en-US", "crm-1", null);
        ContactIdentityService.UpsertIdentityCommand copied =
            new ContactIdentityService.UpsertIdentityCommand("+1555", "en-US", "crm-1", Map.of("source", "form"));

        assertThat(command.attributes()).isEmpty();
        assertThat(copied.attributes()).containsEntry("source", "form");
    }
}
