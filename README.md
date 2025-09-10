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

The generated datasets can be used to support:

- PRECIS implementation testing across Unicode versions
- Impact analysis for Unicode update decisions
- Standards development for PRECIS/Unicode compatibility documents

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


## Findings

### Special Cases in Unicode Version Transitions

During implementation, we discovered several codepoints that require special handling to match the reference data from [I-D draft-nemoto-precis-unicode14-00][3]. These fall into two categories:

#### Documented Special Cases

These codepoints are explicitly discussed in [I-D draft-nemoto-precis-unicode14-00][3]:

##### U+111C9 (SHARADA SANDHI MARK) - Unicode 10.0.0 → 11.0.0
[I-D draft-nemoto-precis-unicode14-00][3] explicitly states: "Change of `SHARADA SANDHI MARK (U+111C9)` added in Unicode 8.0.0 affects PRECIS calculation of the derived property values in IdentifierClass.
PRECIS Derived Property Value of this between Unicode 8.0.0 and Unicode 10.0.0 is `ID_DIS` or `FREE_PVAL`, however in Unicode 11.0.0 is `PVALID`."

- **Anomaly**: This codepoint appears in both "Changes from derived property value UNASSIGNED" and "Changes from derived property value `ID_DIS` or `FREE_PVAL` to `PVALID`" sections of the reference tables
- **Resolution**: The reference tables list the same codepoint in two different sections (possibly an error in the document), we replicate this behavior exactly

##### U+166D (CANADIAN SYLLABICS CHI SIGN) - Unicode 11.0.0 → 12.0.0  

[I-D draft-nemoto-precis-unicode14-00][3] notes: "Unicode General Properties of `CANADIAN SYLLABICS CHI SIGN (U+166D)` was changed from `Po` to `So` in Unicode 12.0.0. This change has changed the basis for calculating of the derived property value from Punctuation (P) in Section 9.16 of RFC 8264 to Symbols (O) in Section 9.15 of RFC 8264. However, this change does not affect the calculation result."

- **Anomaly**: Despite [I-D draft-nemoto-precis-unicode14-00][3] stating the change "does not affect the calculation result," the reference tables list this codepoint as transitioning from `UNASSIGNED` to `FREE_PVAL`
- **Resolution**: Force inclusion as an UNASSIGNED→FREE_PVAL transition to match reference tables (possible documentation inconsistency)

#### Undocumented Discrepancies

These represent cases where our algorithmic derivation differs from the reference tables without explanation in [I-D draft-nemoto-precis-unicode14-00][3]:

##### U+180F (MONGOLIAN FREE VARIATION SELECTOR FOUR) - Unicode 13.0.0 → 14.0.0

[I-D draft-nemoto-precis-unicode14-00][3] makes no special remark about this codepoint. It lists the codepoint as transitioning from `UNASSIGNED` to `DISALLOWED`.

- **Anomaly**: When U+180F becomes assigned in Unicode 14.0.0, it has `General_Category=Mn` (Nonspacing Mark). Following RFC 8264 Section 8's algorithm, `Mn` is included in LetterDigits (Section 9.2) per RFC 5892, which results in `PVALID` categorization. Yet the I-D categorizes it as `DISALLOWED`.
  [I-D draft-nemoto-precis-unicode14-00][3] does not explain why this character should be DISALLOWED rather than the algorithmically derived PVALID
- **Resolution**: Override the algorithmic derivation to force `DISALLOWED` to match reference tables

### Implementation Notes

These special cases are handled in `scripts/generate.clj` within the change table generation logic.
They are evaluated before the general change detection logic to ensure proper precedence. 

The undocumented discrepancies may represent:
- Errors in the reference implementation
- Errors in our algorithmic implementation
- Undocumented policy decisions in the PRECIS specification
- Edge cases in the Unicode data interpretation

Without further clarification from the specification authors, we have chosen to match the reference data exactly to ensure compatibility.

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

## Project Structure

- `scripts/` - Data generation and validation scripts
- `tables-extracted/` - Reference datasets from [T. Nemoto draft][3] and [IANA's precis-tables][2]
- `tables-generated/` - Generated datasets that should match the extracted ones
- `data/` - Final machine-readable datasets showing PRECIS Derived Property Value changes between unicode versions

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
