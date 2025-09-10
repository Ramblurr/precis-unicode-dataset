# PRECIS Unicode Dataset Generator

This project generates machine-readable datasets of PRECIS Framework Derived Property Value changes across Unicode versions to evaluate Unicode update compatibility.

## Background

The PRECIS Framework[1] for internationalized string handling was standardized in RFC 8264 against Unicode 6.3.0, with property classifications registered in the IANA PRECIS tables[2]. However, unlike IDNA2008 which has established procedures for Unicode updates[4], PRECIS has remained locked to Unicode 6.3.0 due to unresolved compatibility concerns.

As stated in Section 11.1 of RFC 8264, the IANA PRECIS registry was explicitly designed not to be updated beyond Unicode 6.3.0 until issues described in the IAB Statement on Unicode 7.0.0[5] and Section 13.5 of RFC 8264 are resolved. These concerns center on:

- **Identifier Stability**: Potential breaking changes to existing string classifications
- **Security Implications**: Character property changes affecting authentication and authorization
- **Normalization Complexity**: Unicode updates introducing new character transformation behaviors

While IDNA2008 faced similar challenges and successfully established an expert review process (RFC 8753[4]) to handle Unicode updates, PRECIS lacks an equivalent framework. T. Nemoto's analysis[3] demonstrates that implementing a similar review model for PRECIS may be a practical solution, but requires comprehensive impact analysis of property value changes across Unicode versions.

**In simpler terms:** When applications handle international text (like usernames with Chinese characters or passwords with emoji), they need rules about which characters are allowed and how to treat them. PRECIS provides these rules, but they're stuck using an old version of Unicode from 2013. Unicode keeps adding new characters and changing how existing ones work, but PRECIS can't update because nobody knows if the changes would break existing software or create security problems. This project analyzes what would happen if PRECIS did update, providing the data needed to make that decision safely.

This project generates the datasets necessary to evaluate whether PRECIS can safely follow Unicode updates without compromising security or breaking existing implementations, providing the foundation for potential PRECIS Framework evolution beyond Unicode 6.3.0.

## Objective

Generate comprehensive datasets showing how PRECIS Derived Property Values change between Unicode versions, starting from the baseline Unicode 6.3.0 through current versions. This data enables analysis of:

- Character property transitions (PVALID, DISALLOWED, UNASSIGNED, etc.)
- Impact of newly assigned codepoints on existing PRECIS profiles
- Compatibility implications for PRECIS implementations
- Safety assessment for Unicode version updates in PRECIS systems

## Data Sources

### Reference Standards
- **IANA PRECIS Tables 6.3.0**[2]: Official baseline property classifications
- **T. Nemoto I-D draft-nemoto-precis-unicode14-00**[3]: Known-good change analysis for Unicode transitions

### Generated Outputs
- Complete property mappings for each Unicode version
- Version-to-version change tables showing property transitions
- Statistical summaries of character classification changes
- Machine-readable formats (CSV, EDN) for automated analysis

## Current Status

**Phase 1: Reference Data Extraction** (In Progress)
- Extracting IANA PRECIS tables[2] (Unicode 6.3.0 baseline)
- Processing T. Nemoto draft[3] tables for validation
- Algorithm verification against known-good datasets

**Phase 2: Dataset Generation** (Planned)
- Complete Unicode version coverage (6.3.0 through latest)
- Automated change detection and classification
- Statistical analysis and compatibility reporting

## Project Structure

- `scripts/` - Data generation and validation scripts
- `tables-extracted/` - Reference datasets from [T. Nemoto draft][3] and [IANA's precis-tables][2]
- `tables-generated/` - Generated datasets that should match the extracted ones
- `data/` - Final machine-readable datasets showing PRECIS Derived Property Value changes between unicode versions

## Usage

Generate and validate datasets:
```bash
# Extract reference data
bb scripts/extract.clj

# Generate new datasets
bb scripts/prepare.clj

# Verify against reference
bb scripts/verify.clj
```

## Applications

The generated datasets support:

- PRECIS implementation testing across Unicode versions
- Impact analysis for Unicode update decisions
- Standards development for PRECIS/Unicode compatibility documents

## References


1. [RFC 8264: PRECIS Framework][1]
2. [IANA PRECIS Tables][2]
3. [Draft Nemoto PRECIS Unicode Analysis][3]
4. [RFC 8753: DNS Terminology][4]
5. [IAB Statement on Identifiers and Unicode 7.0.0][5]

[1]: https://www.rfc-editor.org/rfc/rfc8264.html
[2]: https://www.iana.org/assignments/precis-tables/
[3]: https://datatracker.ietf.org/doc/draft-nemoto-precis-unicode/
[4]: https://www.rfc-editor.org/rfc/rfc8753.html
[5]: https://datatracker.ietf.org/doc/statement-iab-statement-on-identifiers-and-unicode-7-0-0/
