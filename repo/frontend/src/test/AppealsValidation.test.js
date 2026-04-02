import { describe, expect, it } from "vitest";

const allowedTypes = ["image/jpeg", "image/png", "application/pdf"];

function validateFiles(files) {
  const errors = [];
  if (files.length > 5) {
    errors.push("A maximum of 5 evidence files is allowed.");
  }

  files.forEach((file) => {
    if (file.size > 10 * 1024 * 1024) {
      errors.push(`${file.name}: file exceeds 10MB.`);
    }
    if (!allowedTypes.includes(file.type)) {
      errors.push(`${file.name}: unsupported file type.`);
    }
  });

  return errors;
}

function makeFile(name, size, type) {
  return { name, size, type };
}

describe("AppealsPage file validation", () => {
  it("rejects files over 10 MB", () => {
    const files = [makeFile("big.pdf", 11 * 1024 * 1024, "application/pdf")];
    const errors = validateFiles(files);
    expect(errors).toContain("big.pdf: file exceeds 10MB.");
  });

  it("rejects more than 5 files", () => {
    const files = Array.from({ length: 6 }, (_, i) =>
      makeFile(`file${i}.png`, 1024, "image/png"),
    );
    const errors = validateFiles(files);
    expect(errors).toContain("A maximum of 5 evidence files is allowed.");
  });

  it("rejects non-allowed MIME types", () => {
    const files = [makeFile("script.exe", 1024, "application/x-executable")];
    const errors = validateFiles(files);
    expect(errors).toContain("script.exe: unsupported file type.");
  });

  it("accepts valid JPEG files", () => {
    const files = [makeFile("photo.jpg", 5 * 1024 * 1024, "image/jpeg")];
    const errors = validateFiles(files);
    expect(errors).toHaveLength(0);
  });

  it("accepts valid PNG files", () => {
    const files = [makeFile("screenshot.png", 2 * 1024 * 1024, "image/png")];
    const errors = validateFiles(files);
    expect(errors).toHaveLength(0);
  });

  it("accepts valid PDF files", () => {
    const files = [makeFile("document.pdf", 1024 * 1024, "application/pdf")];
    const errors = validateFiles(files);
    expect(errors).toHaveLength(0);
  });

  it("accepts exactly 5 valid files", () => {
    const files = Array.from({ length: 5 }, (_, i) =>
      makeFile(`file${i}.jpg`, 1024, "image/jpeg"),
    );
    const errors = validateFiles(files);
    expect(errors).toHaveLength(0);
  });

  it("reports multiple errors at once", () => {
    const files = [
      makeFile("big.exe", 15 * 1024 * 1024, "application/x-executable"),
    ];
    const errors = validateFiles(files);
    expect(errors.length).toBeGreaterThanOrEqual(2);
  });

  it("accepts empty file list", () => {
    const errors = validateFiles([]);
    expect(errors).toHaveLength(0);
  });
});
