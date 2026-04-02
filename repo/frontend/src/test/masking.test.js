import { describe, expect, it } from "vitest";
import { maskPhone } from "../utils/masking";

describe("maskPhone", () => {
  it("masks a standard phone number", () => {
    expect(maskPhone("5551234567")).toBe("555-***-4567");
  });

  it("masks a phone with dashes", () => {
    expect(maskPhone("555-123-4567")).toBe("555-***-4567");
  });

  it("masks a phone with parentheses and spaces", () => {
    expect(maskPhone("(555) 123-4567")).toBe("555-***-4567");
  });

  it("returns dash for null", () => {
    expect(maskPhone(null)).toBe("-");
  });

  it("returns dash for undefined", () => {
    expect(maskPhone(undefined)).toBe("-");
  });

  it("returns dash for empty string", () => {
    expect(maskPhone("")).toBe("-");
  });

  it("returns *** for very short numbers", () => {
    expect(maskPhone("12")).toBe("***");
  });

  it("handles a long international number", () => {
    const result = maskPhone("+1-555-987-6543");
    expect(result).toBe("155-***-6543");
  });
});
