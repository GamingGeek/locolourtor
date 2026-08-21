import { describe, expect, it } from "vitest";
import { formatUuid, getDefaultColour, isValidUuid } from "./colour";

describe("UUID and Colour utilities", () => {
  it("formats raw UUIDs correctly", () => {
    expect(formatUuid("853c80ef3c3749fdaa49938b674adae6")).toBe(
      "853c80ef-3c37-49fd-aa49-938b674adae6",
    );
  });

  it("validates UUID format", () => {
    expect(isValidUuid("853c80ef-3c37-49fd-aa49-938b674adae6")).toBe(true);
    expect(isValidUuid("invalid-uuid")).toBe(false);
  });

  it("calculates default locator bar color consistently", () => {
    const uuid = "4686e7b5-8815-485d-8bc4-a45445abb984";
    const colour = getDefaultColour(uuid);
    expect(colour.toLowerCase()).toBe("#e6a233");
  });
});
