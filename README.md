# PRECIS Framework Unicode Update Analysis

This study analyzes PRECIS Framework compatibility with Unicode evolution by tracking Derived Property Value changes across major Unicode releases.

The project includes complete tooling for PRECIS property derivation and change analysis, allowing researchers and implementers to reproduce findings and extend analysis to future Unicode releases.


<!-- markdown-toc start - Don't edit this section. Run M-x markdown-toc-refresh-toc -->
**Table of Contents**

- [PRECIS Framework Unicode Update Analysis](#precis-framework-unicode-update-analysis)
  - [Background](#background)
    - [The "hamza" problem ](#the-hamza-problem)
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

The PRECIS Framework [[RFC8264]](#RFC8264) defines two string classes for use in application protocols: IdentifierClass, for identifiers such as usernames, and FreeformClass, for strings such as passwords.
Each class is defined by Derived Property Values, which are computed from properties defined in the Unicode Standard.
These values are published in the IANA PRECIS Derived Property Value registry [[IANA-PRECIS]](#IANA-PRECIS).

While PRECIS was designed to be Unicode-agile, permitting implementations to calculate Derived Property Values against newer versions of Unicode, the IANA registry itself has remained frozen at Unicode 6.3.0.
Section 11.1 of RFC 8264 specifies that the registry must not be updated until issues identified in Section 13.5 of the RFC and in the [[IAB Statement on Unicode 7.0.0]](#IAB-UNICODE7) are resolved. 

While IDNA2008 faced similar challenges and successfully established an expert review process [[RFC8753]](#RFC8753) to handle Unicode updates and as of March 2022 the IAB issue was  definitively resolved in [[RFC9233]](#RFC9233).

However, PRECIS lacks an equivalent review framework.
T. Nemoto's analysis in [[NEMOTO-DRAFT]](#NEMOTO-DRAFT) demonstrates that implementing a similar review model for PRECIS may be a practical solution, but requires comprehensive impact analysis of property value changes across Unicode versions.

This project seeks to provide that impact analysis.
By generating and publishing comparative datasets of PRECIS Derived Property Values across Unicode versions, this project provides the empirical basis for assessing identifier stability, security impacts, and normalization issues.

The goal is to enable informed consideration of whether PRECIS can adopt a Unicode update model similar to IDNA2008's, thereby supporting its evolution beyond Unicode 6.3.0.

### The "hamza" problem 

The introduction of ARABIC LETTER BEH WITH HAMZA ABOVE (U+08A1) in Unicode 7.0.0 exposed a difference between precomposed and decomposed sequences in Arabic script [[IAB2005-1]](#IAB2005-1). The character U+08A1 can also be expressed as ARABIC LETTER BEH (U+0628) followed by ARABIC HAMZA ABOVE (U+0654).

Unlike similar cases in the Latin script such as a-diaeresis, the precomposed and decomposed forms of beh-with-hamza are not canonically equivalent in Unicode normalization [[IAB2005-2]](#IAB2005-2).

As a result, normalization does not collapse U+08A1 and U+0628+U+0654 into the same string, leaving them distinct code point sequences [[RFC9233]](#RFC9233).
This creates the potential for spoofing in identifiers, because two visually indistinguishable usernames can coexist while being treated as different by PRECIS-based comparison.

For IDNA2008, the IETF studied the problem and decided that no exception should be introduced, leaving U+08A1 as PVALID and relying on strengthened review processes defined in RFC 8753 [[RFC9233]](#RFC9233).
RFC 9233 documented this outcome and explicitly recognized that U+08A1 is not an isolated case, since similar combining issues exist elsewhere in Unicode (though the RFC does not list any).
For PRECIS, however, no update has been published beyond RFC 8264, and the framework has not yet been aligned to newer Unicode versions in IANA registries [[RFC8264]](#RFC8264).

This means the question of how to handle U+08A1 and similar cases remains unresolved in PRECIS, at least at the standards level.
Applications and profiles that use PRECIS may need to impose their own restrictions or confusable-detection measures to avoid spoofing attacks.

The facts are clear: PRECIS permits both forms as valid, Unicode normalization does not unify them, and the potential for confusion remains.

Whether this is regarded as a "solved" problem for PRECIS is therefore an open question, and the implications must be weighed by implementers and reviewers in each deployment context

In any case, this project is not focused on the hamza problem and mentions it as an important other open question in this space.

## Objective

Generate comprehensive datasets showing how PRECIS Derived Property Values change between Unicode versions, starting from the baseline Unicode 6.3.0 through current versions.

This data enables analysis of:

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

- Unicode Character Database (UCD) [[UCD]](#UCD)
- IANA precis-tables-6.3.0 [[IANA-PRECIS]](#IANA-PRECIS): Official baseline property classifications
- T. Nemoto I-D draft-nemoto-precis-unicode14-00 [[NEMOTO-DRAFT]](#NEMOTO-DRAFT): Known-good change analysis for Unicode transitions

### Generated Outputs
- Complete property mappings for each Unicode version
- Version-to-version change tables showing property transitions
- Statistical summaries of character classification changes

## Findings

### Discrepancies

During implementation, we discovered several codepoints that require special handling to match the reference data from [[NEMOTO-DRAFT]](#NEMOTO-DRAFT). 
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

**Anomaly**:  Following RFC 8264 Section 8's algorithm, `Mn` is included in [[LetterDigits (RFC 8264 Section 9.1)]](#RFC8264-SECTION91) which forwards to [[RFC 5892 2.1]](#RFC5892-SECTION21), which results in PVALID categorization.

Yet I-D draft-nemoto-precis-unicode14-00 categorizes FVS4 as DISALLOWED.
The I-D does not explain why this character should be DISALLOWED rather than the algorithmically derived PVALID.

However the anomaly deepens when looking at codepoints U+180B-180D MONGOLIAN FREE VARIATION SELECTOR ONE through THREE (FVS1 - FVS3). 
These codepoints are semantically similar to FVS4 and should also be algorithmically derived as PVALID, but they are listed in the IANA precis-tables-6.3.0 as DISALLOWED.
It is not clear what rules or source material were considered in the construction of IANA precis-tables-6.3.0. Notably they do not exist in the Exceptions (F) category.
A review of RFC 5892, its [[errata]](#RFC5892-ERRATA), and the update in [[RFC 8753]](#RFC8753) do not shed any light on the mystery.


**Resolution**: Override the algorithmic derivation to force DISALLOWED to match reference tables

The classification as DISALLOWED follows established IETF expert precedent: FVS1-3 (U+180B-180D) are classified as DISALLOWED in IANA PRECIS tables despite having identical Unicode properties (General Category "Mn") that would algorithmically produce PVALID. 

The DISALLOWED resolution is supported by two key factors:

1. Expert precedent: FVS1-3 are DISALLOWED in IANA precis-tables-6.3.0, establishing that these mongolian variation selectors require special exceptions
2. Practical usage considerations, as the [[Unicode proposal for FVS4]](#FVS4-PROPOSAL) states it "would only be needed by pre-contemporary, historical texts," indicating this character is not intended for modern text input that would appear in PRECIS systems like usernames, identifiers, or passwords


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
- `tables-extracted/` - Reference datasets from [[T. Nemoto draft]](#NEMOTO-DRAFT) and [[IANA's precis-tables]](#IANA-PRECIS)
- `tables-generated/` - Generated datasets that should match the extracted ones
- `data/` - Final machine-readable datasets showing PRECIS Derived Property Value changes between unicode versions (**TODO**: this is not yet done)

## References

<a id="FVS4-PROPOSAL">[FVS4-PROPOSAL]</a>  
Anderson, D. (2020).  
Mongolian Free Variation Selector Four.  
Unicode Technical Committee Document L2/20-057. <https://www.unicode.org/L2/L2020/20057-mongolian-fvs4.pdf>

<a id="IAB-UNICODE7">[IAB-UNICODE7]</a>  
Internet Architecture Board (2015).  
IAB Statement on Identifiers and Unicode 7.0.0.  
<https://datatracker.ietf.org/doc/statement-iab-statement-on-identifiers-and-unicode-7-0-0/>

<a id="IAB2005-1">[IAB2005-1]</a>  
Internet Architecture Board (2015, January 27).  
IAB Statement on Identifiers and Unicode 7.0.0.  
<https://www.iab.org/documents/correspondence-reports-documents/2015-2/iab-statement-on-identifiers-and-unicode-7-0-0/archive/>

<a id="IAB2005-2">[IAB2005-2]</a>  
Internet Architecture Board (2015, February 11).  
IAB Statement on Identifiers and Unicode 7.0.0.  
<https://www.iab.org/documents/correspondence-reports-documents/2015-2/iab-statement-on-identifiers-and-unicode-7-0-0/>

<a id="IANA-PRECIS">[IANA-PRECIS]</a>  
Internet Assigned Numbers Authority.  
PRECIS Derived Property Value Registry.  
<https://www.iana.org/assignments/precis-tables/>

<a id="NEMOTO-DRAFT">[NEMOTO-DRAFT]</a>  
Nemoto, T.  
Analysis of PRECIS Framework update with Unicode 14.0.0.  
Internet-Draft draft-nemoto-precis-unicode14-00. <https://datatracker.ietf.org/doc/draft-nemoto-precis-unicode/>

<a id="RFC5892-ERRATA">[RFC5892-ERRATA]</a>  
RFC Editor.  
RFC 5892 Errata.  
<https://www.rfc-editor.org/errata/rfc5892>

<a id="RFC5892-SECTION21">[RFC5892-SECTION21]</a>  
Faltstrom, P. (2010).  
The Unicode Code Points and Internationalized Domain Names for Applications (IDNA).  
RFC 5892, Section 2.1. <https://www.rfc-editor.org/rfc/rfc5892#section-2.1>

<a id="RFC8264">[RFC8264]</a>  
Saint-Andre, P. and M. Blanchet (2017).  
PRECIS Framework: Preparation, Enforcement, and Comparison of Internationalized Strings in Application Protocols.  
RFC 8264. <https://www.rfc-editor.org/info/rfc8264>

<a id="RFC8264-SECTION91">[RFC8264-SECTION91]</a>  
Saint-Andre, P. and M. Blanchet (2017).  
PRECIS Framework: Preparation, Enforcement, and Comparison of Internationalized Strings in Application Protocols.  
RFC 8264, Section 9.1. <https://www.rfc-editor.org/rfc/rfc8264.html#section-9.1>

<a id="RFC8753">[RFC8753]</a>  
Klensin, J. and P. Fältström (2020).  
Internationalized Domain Names for Applications (IDNA) Review for New Unicode Versions.  
RFC 8753. <https://www.rfc-editor.org/info/rfc8753>

<a id="RFC9233">[RFC9233]</a>  
Fältström, P. (2022).  
Internationalized Domain Names for Applications 2008 (IDNA2008) and Unicode 12.0.0.  
RFC 9233. <https://www.rfc-editor.org/info/rfc9233>

<a id="UCD">[UCD]</a>  
The Unicode Consortium.  
Unicode Character Database.  
<https://www.unicode.org/ucd/>

