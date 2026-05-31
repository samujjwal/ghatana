import {
  PHR_MOBILE_PHI_FIELD_POLICIES,
  shouldRemoveFieldFromMobileCache,
} from "../mobilePhiPolicy";

describe("mobilePhiPolicy", () => {
  it("uses Kernel mobile PHI field policy to block restricted cache fields", () => {
    expect(shouldRemoveFieldFromMobileCache("mentalHealth")).toBe(true);
    expect(shouldRemoveFieldFromMobileCache("substanceUse")).toBe(true);
    expect(shouldRemoveFieldFromMobileCache("geneticInfo")).toBe(true);
    expect(shouldRemoveFieldFromMobileCache("reproductiveHealth")).toBe(true);
    expect(shouldRemoveFieldFromMobileCache("hivStatus")).toBe(true);
    expect(shouldRemoveFieldFromMobileCache("nationalId")).toBe(true);
  });

  it("does not purge structural dashboard fields that have no field policy", () => {
    expect(shouldRemoveFieldFromMobileCache("patient")).toBe(false);
    expect(shouldRemoveFieldFromMobileCache("records")).toBe(false);
    expect(shouldRemoveFieldFromMobileCache("summary")).toBe(false);
  });

  it("declares restricted fields as not cacheable in the policy", () => {
    expect(PHR_MOBILE_PHI_FIELD_POLICIES).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          fieldName: "mentalHealth",
          classification: "restricted",
          cacheAllowed: false,
        }),
        expect.objectContaining({
          fieldName: "hivStatus",
          classification: "restricted",
          cacheAllowed: false,
        }),
      ]),
    );
  });
});
