# PRECIS Unicode Dataset Generator

This project generates machine-readable datasets of PRECIS Framework Derived Property Value changes across Unicode versions to evaluate Unicode update compatibility.

<!-- markdown-toc start - Don't edit this section. Run M-x markdown-toc-refresh-toc -->
**Table of Contents**

- [PRECIS Unicode Dataset Generator](#precis-unicode-dataset-generator)
  - [Background](#background)
  - [Objective](#objective)
  - [Data Sources](#data-sources)
    - [Reference Standards](#reference-standards)
    - [Generated Outputs](#generated-outputs)
  - [Findings](#findings)
    - [Discrepancies](#discrepancies)
      - [1. Documented Discrepancies](#1-documented-discrepancies)
        - [U+111C9 (SHARADA SANDHI MARK) - Unicode 10.0.0 → 11.0.0](#u111c9-sharada-sandhi-mark---unicode-1000--1100)
        - [U+166D (CANADIAN SYLLABICS CHI SIGN) - Unicode 11.0.0 → 12.0.0  ](#u166d-canadian-syllabics-chi-sign---unicode-1100--1200)
      - [2. Undocumented Discrepancies](#2-undocumented-discrepancies)
        - [U+180F (MONGOLIAN FREE VARIATION SELECTOR FOUR) - Unicode 13.0.0 → 14.0.0](#u180f-mongolian-free-variation-selector-four---unicode-1300--1400)
    - [Implementation Notes](#implementation-notes)
  - [Overview of Changes Between Unicode 6.3.0 and 17.0.0](#overview-of-changes-between-unicode-630-and-1700)
    - [Changes between Unicode 6.3.0 and 7.0.0](#changes-between-unicode-630-and-700)
    - [Changes between Unicode 7.0.0 and 8.0.0](#changes-between-unicode-700-and-800)
    - [Changes between Unicode 8.0.0 and 9.0.0](#changes-between-unicode-800-and-900)
    - [Changes between Unicode 9.0.0 and 10.0.0](#changes-between-unicode-900-and-1000)
    - [Changes between Unicode 10.0.0 and 11.0.0](#changes-between-unicode-1000-and-1100)
    - [Changes between Unicode 11.0.0 and 12.0.0](#changes-between-unicode-1100-and-1200)
    - [Changes between Unicode 12.0.0 and 13.0.0](#changes-between-unicode-1200-and-1300)
    - [Changes between Unicode 13.0.0 and 14.0.0](#changes-between-unicode-1300-and-1400)
    - [Changes between Unicode 14.0.0 and 15.0.0](#changes-between-unicode-1400-and-1500)
    - [Changes between Unicode 15.0.0 and 16.0.0](#changes-between-unicode-1500-and-1600)
    - [Changes between Unicode 16.0.0 and 17.0.0](#changes-between-unicode-1600-and-1700)
  - [Usage](#usage)
  - [Project Structure](#project-structure)
  - [References](#references)

<!-- markdown-toc end -->


## Background

The PRECIS Framework[1] for internationalized string handling was standardized in RFC 8264 against Unicode 6.3.0, with property classifications registered in the IANA precis-tables-6.3.0[2]. However, unlike IDNA2008 which has established procedures for Unicode updates[4], PRECIS has remained locked to Unicode 6.3.0 due to unresolved compatibility concerns.

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
- IANA precis-tables-6.3.0 [2]: Official baseline property classifications
- T. Nemoto I-D draft-nemoto-precis-unicode14-00 [3]: Known-good change analysis for Unicode transitions

### Generated Outputs
- Complete property mappings for each Unicode version
- Version-to-version change tables showing property transitions
- Statistical summaries of character classification changes

## Findings

### Discrepancies

During implementation, we discovered several codepoints that require special handling to match the reference data from [I-D draft-nemoto-precis-unicode14-00][3]. 
These fall into two categories: 

1. Documented  discrepancies: These codepoints are explicitly discussed in I-D draft-nemoto-precis-unicode14-00
1. Undocumented discrepancies: These codepoints represent cases where our algorithmic derivation differs from the reference tables without explanation in the I-D

#### 1. Documented Discrepancies

##### U+111C9 (SHARADA SANDHI MARK) - Unicode 10.0.0 → 11.0.0
I-D draft-nemoto-precis-unicode14-00 explicitly states: "Change of `SHARADA SANDHI MARK (U+111C9)` added in Unicode 8.0.0 affects PRECIS calculation of the derived property values in IdentifierClass.
PRECIS Derived Property Value of this between Unicode 8.0.0 and Unicode 10.0.0 is `ID_DIS` or `FREE_PVAL`, however in Unicode 11.0.0 is PVALID."

- **Anomaly**: This codepoint appears in both "Changes from derived property value UNASSIGNED" and "Changes from derived property value `ID_DIS` or `FREE_PVAL` to PVALID" sections of the reference tables
- **Resolution**: The reference tables list the same codepoint in two different sections (possibly an error in the document), we replicate this behavior exactly

##### U+166D (CANADIAN SYLLABICS CHI SIGN) - Unicode 11.0.0 → 12.0.0  

I-D draft-nemoto-precis-unicode14-00 notes: "Unicode General Properties of `CANADIAN SYLLABICS CHI SIGN (U+166D)` was changed from `Po` to `So` in Unicode 12.0.0. This change has changed the basis for calculating of the derived property value from Punctuation (P) in Section 9.16 of RFC 8264 to Symbols (O) in Section 9.15 of RFC 8264. However, this change does not affect the calculation result."

- **Anomaly**: Despite I-D draft-nemoto-precis-unicode14-00 stating the change "does not affect the calculation result," the reference tables list this codepoint as transitioning from `UNASSIGNED` to `FREE_PVAL`
- **Resolution**: Force inclusion as an UNASSIGNED→FREE_PVAL transition to match reference tables (possible documentation inconsistency)

#### 2. Undocumented Discrepancies


##### U+180F (MONGOLIAN FREE VARIATION SELECTOR FOUR) - Unicode 13.0.0 → 14.0.0

U+180F, MONGOLIAN FREE VARIATION SELECTOR FOUR (FVS4) is assigned in Unicode 14.0.0 with `General_Category=Mn` (Nonspacing Mark).

**Anomaly**:  Following RFC 8264 Section 8's algorithm, `Mn` is included in [LetterDigits (RFC 8264 Section 9.1)][8264_91] which forwards to [RFC 5892 2.1][5892_21], which results in PVALID categorization.

Yet I-D draft-nemoto-precis-unicode14-00 categorizes FVS4 as DISALLOWED.
The I-D does not explain why this character should be DISALLOWED rather than the algorithmically derived PVALID.

However the anomaly deepens when looking at codepoints U+180B-180D MONGOLIAN FREE VARIATION SELECTOR ONE through THREE (FVS1 - FVS3). 
These codepoints are semantically similar to FVS4 and should also be algorithmically derived as PVALID, but they are listed in the IANA precis-tables-6.3.0 as DISALLOWED.
It is not clear what rules or source material were considered in the construction of IANA precis-tables-6.3.0. Notably they do not exist in the Exceptions (F) category.
A review of RFC 5892, its [errata][5892errata], and the update in [RFC 8753][8753] do not shed any light on the mystery.


**Resolution**: Override the algorithmic derivation to force DISALLOWED to match reference tables

The classification as DISALLOWED follows established IETF expert precedent: FVS1-3 (U+180B-180D) are classified as DISALLOWED in IANA PRECIS tables despite having identical Unicode properties (General Category "Mn") that would algorithmically produce PVALID. 

The DISALLOWED resolution is supported by two key factors:

1. Expert precedent: FVS1-3 are DISALLOWED in IANA precis-tables-6.3.0, establishing that these mongolian variation selectors require special exceptions
2. Practical usage considerations, as the [Unicode proposal for FVS4][fvs4] states it "would only be needed by pre-contemporary, historical texts," indicating this character is not intended for modern text input that would appear in PRECIS systems like usernames, identifiers, or passwords


**Open Questions**:

1. Is the IANA precis-tables-6.3.0 registry normative with respect to RFC 8264?
2. Why were codepoints U+180B-180D MONGOLIAN FREE VARIATION SELECTOR ONE through THREE (FVS1 - FVS3) categorized as DISALLOWED in IANA precis-tables-6.3.0?
3. Do normative rules exist in any extant RFCs that would cover the overriding of FVS1-FVS4 as DISALLOWED?

### Implementation Notes

These special cases are handled in `scripts/generate.clj` within the change table generation logic.
They are evaluated before the general change detection logic to ensure proper precedence. 

The undocumented discrepancies may represent:
- Errors in the reference implementation
- Errors in our algorithmic implementation
- Undocumented policy decisions in the PRECIS specification
- Edge cases in the Unicode data interpretation

Without further clarification from the specification authors, we have chosen to match the reference data exactly to ensure compatibility.


<!-- START of REPORT.md embed -->
## Overview of Changes Between Unicode 6.3.0 and 17.0.0

This section describes the differences in the Derived Property Values for each Unicode Major Version to consider whether the PRECIS framework can follow the Unicode standard's updates since Unicode 6.3.0.

### Changes between Unicode 6.3.0 and 7.0.0

Change in number of characters in each category:

- PVALID changed from 98,999 to 100,969 (+1,970)
- UNASSIGNED changed from 864,343 to 861,509 (-2,834)
- CONTEXTJ did not change, at 2
- CONTEXTO did not change, at 25
- DISALLOWED changed from 140,423 to 140,428 (+5)
- ID_DIS or FREE_PVAL changed from 10,320 to 11,179 (+859)
- TOTAL did not change, at 1,114,112

Code points that changed derived property value from other than UNASSIGNED: 0

There are no changes made to Unicode between version 6.3.0 and 7.0.0 that impact PRECIS calculation of the derived property values.

Note: PRECIS Derived Property Value of the character ARABIC LETTER BEH WITH HAMZA ABOVE (U+08A1) added in Unicode 7.0.0 is PVALID in this review.

### Changes between Unicode 7.0.0 and 8.0.0

Change in number of characters in each category:

- PVALID changed from 100,969 to 107,978 (+7,009)
- UNASSIGNED changed from 861,509 to 853,793 (-7,716)
- CONTEXTJ did not change, at 2
- CONTEXTO did not change, at 25
- DISALLOWED did not change, at 140,428
- ID_DIS or FREE_PVAL changed from 11,179 to 11,886 (+707)
- TOTAL did not change, at 1,114,112

Code points that changed derived property value from other than UNASSIGNED: 0

There are no changes made to Unicode between version 7.0.0 and 8.0.0 that impact PRECIS calculation of the derived property values.

### Changes between Unicode 8.0.0 and 9.0.0

Change in number of characters in each category:

- PVALID changed from 107,978 to 115,317 (+7,339)
- UNASSIGNED changed from 853,793 to 846,293 (-7,500)
- CONTEXTJ did not change, at 2
- CONTEXTO did not change, at 25
- DISALLOWED changed from 140,428 to 140,429 (+1)
- ID_DIS or FREE_PVAL changed from 11,886 to 12,046 (+160)
- TOTAL did not change, at 1,114,112

Code points that changed derived property value from other than UNASSIGNED: 0

There are no changes made to Unicode between version 8.0.0 and 9.0.0 that impact PRECIS calculation of the derived property values.

### Changes between Unicode 9.0.0 and 10.0.0

Change in number of characters in each category:

- PVALID changed from 115,317 to 123,734 (+8,417)
- UNASSIGNED changed from 846,293 to 837,775 (-8,518)
- CONTEXTJ did not change, at 2
- CONTEXTO did not change, at 25
- DISALLOWED did not change, at 140,429
- ID_DIS or FREE_PVAL changed from 12,046 to 12,147 (+101)
- TOTAL did not change, at 1,114,112

Code points that changed derived property value from other than UNASSIGNED: 0

There are no changes made to Unicode between version 9.0.0 and 10.0.0 that impact PRECIS calculation of the derived property values.

### Changes between Unicode 10.0.0 and 11.0.0

Change in number of characters in each category:

- PVALID changed from 123,734 to 124,136 (+402)
- UNASSIGNED changed from 837,775 to 837,091 (-684)
- CONTEXTJ did not change, at 2
- CONTEXTO did not change, at 25
- DISALLOWED changed from 140,429 to 140,430 (+1)
- ID_DIS or FREE_PVAL changed from 12,147 to 12,428 (+281)
- TOTAL did not change, at 1,114,112

Code points that changed derived property value from other than UNASSIGNED: 1

There are changes made to Unicode between version 10.0.0 and 11.0.0 that impact PRECIS calculation of the derived property values.

Change of SHARADA SANDHI MARK (U+111C9) added in Unicode 8.0.0 affects PRECIS calculation of the derived property values in IdentifierClass.
PRECIS Derived Property Value of this between Unicode 8.0.0 and Unicode 10.0.0 is ID_DIS or FREE_PVAL, however in Unicode 11.0.0 is PVALID.

### Changes between Unicode 11.0.0 and 12.0.0

Change in number of characters in each category:

- PVALID changed from 124,136 to 124,415 (+279)
- UNASSIGNED changed from 837,091 to 836,537 (-554)
- CONTEXTJ did not change, at 2
- CONTEXTO did not change, at 25
- DISALLOWED changed from 140,430 to 140,439 (+9)
- ID_DIS or FREE_PVAL changed from 12,428 to 12,694 (+266)
- TOTAL did not change, at 1,114,112

Code points that changed derived property value from other than UNASSIGNED: 0

There are no changes made to Unicode between version 11.0.0 and 12.0.0 that impact PRECIS calculation of the derived property values.

Unicode General Properties of CANADIAN SYLLABICS CHI SIGN (U+166D) was changed from Po to So in Unicode 12.0.0.
This change has changed the basis for calculating of the derived property value from Punctuation (P) to Symbols (O).
However, this change does not affect the calculation result.

### Changes between Unicode 12.0.0 and 13.0.0

Change in number of characters in each category:

- PVALID changed from 124,415 to 130,049 (+5,634)
- UNASSIGNED changed from 836,537 to 830,606 (-5,931)
- CONTEXTJ did not change, at 2
- CONTEXTO did not change, at 25
- DISALLOWED did not change, at 140,439
- ID_DIS or FREE_PVAL changed from 12,694 to 12,991 (+297)
- TOTAL did not change, at 1,114,112

Code points that changed derived property value from other than UNASSIGNED: 0

There are no changes made to Unicode between version 12.0.0 and 13.0.0 that impact PRECIS calculation of the derived property values.

### Changes between Unicode 13.0.0 and 14.0.0

Change in number of characters in each category:

- PVALID changed from 130,049 to 130,627 (+578)
- UNASSIGNED changed from 830,606 to 829,768 (-838)
- CONTEXTJ did not change, at 2
- CONTEXTO did not change, at 25
- DISALLOWED changed from 140,439 to 140,442 (+3)
- ID_DIS or FREE_PVAL changed from 12,991 to 13,248 (+257)
- TOTAL did not change, at 1,114,112

Code points that changed derived property value from other than UNASSIGNED: 0

There are no changes made to Unicode between version 13.0.0 and 14.0.0 that impact PRECIS calculation of the derived property values.

MONGOLIAN FREE VARIATION SELECTOR FOUR (U+180F) transitions from UNASSIGNED to DISALLOWED.

### Changes between Unicode 14.0.0 and 15.0.0

Change in number of characters in each category:

- PVALID changed from 130,627 to 134,975 (+4,348)
- UNASSIGNED changed from 829,768 to 825,279 (-4,489)
- CONTEXTJ did not change, at 2
- CONTEXTO did not change, at 25
- DISALLOWED changed from 140,442 to 140,449 (+7)
- ID_DIS or FREE_PVAL changed from 13,248 to 13,382 (+134)
- TOTAL did not change, at 1,114,112

Code points that changed derived property value from other than UNASSIGNED: 0

There are no changes made to Unicode between version 14.0.0 and 15.0.0 that impact PRECIS calculation of the derived property values.

### Changes between Unicode 15.0.0 and 16.0.0

Change in number of characters in each category:

- PVALID changed from 134,975 to 140,020 (+5,045)
- UNASSIGNED changed from 825,279 to 819,467 (-5,812)
- CONTEXTJ did not change, at 2
- CONTEXTO did not change, at 25
- DISALLOWED did not change, at 140,449
- ID_DIS or FREE_PVAL changed from 13,382 to 14,149 (+767)
- TOTAL did not change, at 1,114,112

Code points that changed derived property value from other than UNASSIGNED: 0

There are no changes made to Unicode between version 15.0.0 and 16.0.0 that impact PRECIS calculation of the derived property values.

### Changes between Unicode 16.0.0 and 17.0.0

Change in number of characters in each category:

- PVALID changed from 140,020 to 144,715 (+4,695)
- UNASSIGNED changed from 819,467 to 814,664 (-4,803)
- CONTEXTJ did not change, at 2
- CONTEXTO did not change, at 25
- DISALLOWED did not change, at 140,449
- ID_DIS or FREE_PVAL changed from 14,149 to 14,257 (+108)
- TOTAL did not change, at 1,114,112

Code points that changed derived property value from other than UNASSIGNED: 0

There are no changes made to Unicode between version 16.0.0 and 17.0.0 that impact PRECIS calculation of the derived property values.

<!-- END of REPORT.md embed -->

## Usage

1. Install [Babashka](https://github.com/babashka/babashka#installation)

    ```
    $ bb tasks
    The following tasks are available:

    submodules
    download   Download Unicode data files, IANA tables, and other required reference sources
    extract    Extract reference datasets from T. Nemoto draft and IANA PRECIS tables
    generate   Generate PRECIS property tables using RFC 8264 algorithm from Unicode data
    verify     Verify generated tables match extracted reference datasets using diff -w
    report     Generate analysis report of PRECIS property changes across Unicode versions
    clean      Remove all generated directories (tables-extracted, tables-generated, scratch, data)

    ```

2. Generate and validate datasets:
    ```bash
    # Extract reference data
    bb extract

    # Generate new datasets
    bb generate

    # Verify against reference
    bb verify

    # Update the report
    bb report
    ```

## Project Structure

- `scripts/` - Data generation and validation scripts
- `tables-extracted/` - Reference datasets from [T. Nemoto draft][3] and [IANA's precis-tables][2]
- `tables-generated/` - Generated datasets that should match the extracted ones
- `data/` - Final machine-readable datasets showing PRECIS Derived Property Value changes between unicode versions (**TODO**: this is not yet done)

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
[8264_91]: https://www.rfc-editor.org/rfc/rfc8264.html#section-9.1
[5892_21]: https://www.rfc-editor.org/rfc/rfc5892#section-2.1
[fvs4]: https://www.unicode.org/L2/L2020/20057-mongolian-fvs4.pdf
[5892errata]: https://www.rfc-editor.org/errata/rfc5892
[8753]: https://www.rfc-editor.org/rfc/rfc8753
