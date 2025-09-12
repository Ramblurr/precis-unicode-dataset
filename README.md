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
    - [U+111C9 (SHARADA SANDHI MARK) - Unicode 10.0.0 → 11.0.0](#u111c9-sharada-sandhi-mark---unicode-1000--1100)
    - [U+166D (CANADIAN SYLLABICS CHI SIGN) - Unicode 11.0.0 → 12.0.0  ](#u166d-canadian-syllabics-chi-sign---unicode-1100--1200)
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
  - [Generated Dataset Formats](#generated-dataset-formats)
    - [Derived Property Values](#derived-property-values)
    - [Output Files](#output-files)
      - [`allcodepoints.txt`](#allcodepointstxt)
    - [`derived-props-M.m.txt`](#derived-props-mmtxt)
      - [`byscript.html` and `bygc.html`](#byscripthtml-and-bygchtml)
      - [`xmlrfc.xml`](#xmlrfcxml)
      - [`idnabis-tables.xml`](#idnabis-tablesxml)
      - [`iana.csv`](#ianacsv)
  - [References](#references)

<!-- markdown-toc end -->


## Background

The PRECIS Framework [[RFC8264]](#RFC8264) defines two string classes for use in application protocols: IdentifierClass, for identifiers such as usernames, and FreeformClass, for strings such as passwords.
Each class is defined by Derived Property Values, which are computed from properties defined in the Unicode Standard.
These values are published in the IANA PRECIS Derived Property Value registry [[IANA-PRECIS]](#IANA-PRECIS).

While PRECIS was designed to be Unicode-agile, permitting implementations to calculate Derived Property Values against newer versions of Unicode, the IANA registry itself has remained frozen at Unicode 6.3.0.
Section 11.1 of RFC 8264 specifies that the registry must not be updated until issues identified in Section 13.5 of the RFC and in the [[IAB Statement on Unicode 7.0.0]](#IAB-UNICODE7) are resolved (see "The hamza problem" below). 

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

Whether this is regarded as a "solved" problem for PRECIS is therefore an open question, and the implications must be weighed by implementers and reviewers.

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

During implementation, we discovered several codepoints that require special handling to match the reference data from [[NEMOTO-DRAFT]](#NEMOTO-DRAFT). 

### U+111C9 (SHARADA SANDHI MARK) - Unicode 10.0.0 → 11.0.0

U+111C9 (SHARADA SANDHI MARK) was added in Unicode 8.0.0 and had the PRECIS Derived Property Value of ID_DIS or FREE_PVAL.

However in Unicode version 11.0.0 the Unicode properties for U+111C9 were changed as documented in Unicode proposal L2/17-247 [[L2/17-247]](#L2-17-247):
the General Category was changed from Po (Other_Punctuation) to Mn (Nonspacing_Mark), and the Bidi property was changed from L (Left to Right) to NSM (Nonspacing Mark).
This property change caused the derived property value to change to PVALID.

This does not affect the PRECIS FreeformClass, but it does affect the IdentifierClass.
The IdentifierClass includes the LetterDigits (A) category as defined in RFC 8264 Section 9.1, which references RFC 5892 Section 2.1.
The LetterDigits category explicitly includes Mn (Nonspacing_Mark) characters.
When U+111C9 changed from Po to Mn, it became eligible for inclusion in IdentifierClass, changing its derived property value from ID_DIS or FREE_PVAL to PVALID.

This change from disallowed to allowed is not a backwards incompatible change for strings already stored and processed by PRECIS implementations.

**Anomaly**

I-D draft-nemoto-precis-unicode14-00 includes this codepoint in two tables, once in the "Changes from derived propery value ID_DIS or FREE_PVAL to PVALID" section, and again in the "Changes from derived propery value UNASSIGNED to either PVALID.."

**Resolution**

The inclusion of U+11C9 by T. Nemoto  in the second table is is an error.

---

### U+166D (CANADIAN SYLLABICS CHI SIGN) - Unicode 11.0.0 → 12.0.0  

In Unicode 12.0.0 the Unicode properties for U+166D were changed as documented in Unicode Technical Committee Meeting 157 decision [157-C16] [[L2/18-272]](#L2-18-272):
the General Category was changed from Po (Other_Punctuation) to So (Other_Symbol), and the Terminal_Punctuation property was changed from Yes to No.

I-D draft-nemoto-precis-unicode14-00 correctly notes that this property change "does not affect the calculation result" because both Po and So map to the same PRECIS derived property value (ID_DIS or FREE_PVAL).

The change altered the basis for calculating the derived property value from Punctuation (P) in RFC 8264 Section 9.16 to Symbols (O) in RFC 8264 Section 9.15, but both categories result in ID_DIS or FREE_PVAL.

**Anomaly**

I-D draft-nemoto-precis-unicode14-00 includes this codepoint in the table "Changes from derived propery value UNASSIGNED to either PVALID..", but this codepoint was already assigned.

**Resolution**

The inclusion of U+166D by T. Nemoto  in the "form unassigned" table is is an error, it should have been in a different table.

### Implementation Notes

The above findings can be seen by running the `bb verify` command

```
━━━ VALIDATE AGAINST IANA 6.3.0 REGISTRY ━━━
Total codepoints checked: 1114112
Matches: 1114112
Mismatches: 0
Accuracy: 100.00%

Property distribution:
  :contextj: 2
  :contexto: 25
  :disallowed: 140423
  :free-pval: 10320
  :pvalid: 98999
  :unassigned: 864343

PASSED

━━━ VALIDATE AGAINST PYTHON DERIVED-PROPS ━━━
Extracted Python files: 10
Generated Python files: 12
Common Python files: 10
Files beyond Python coverage (ignored): 1

PASSED

━━━ VALIDATE AGAINST draft-nemoto-precis-unicode14-00 CHANGE TABLES ━━━
Extracted files: 9
Generated files: 12
Common files: 9
Files beyond draft coverage (ignored): 3
File differences summary

▶ changes-10.0.0-11.0.0-from-unassigned.txt
  Lines removed from extracted: 1
  Lines added in generated: 0

  First differences (max 10 each):
  Extracted (expected):
    -111C9       ; PVALID      # SHARADA SANDHI MARK

▶ changes-11.0.0-12.0.0-from-unassigned.txt
  Lines removed from extracted: 1
  Lines added in generated: 0

  First differences (max 10 each):
  Extracted (expected):
    -166D        ; FREE_PVAL   # CANADIAN SYLLABICS CHI SIGN

FAILED
```


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

Note: Change of SHARADA SANDHI MARK (U+111C9) affects PRECIS calculation of the derived property values in IdentifierClass.
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

Note: Unicode General Properties of CANADIAN SYLLABICS CHI SIGN (U+166D) was changed from Po to So in Unicode 12.0.0.
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

- PVALID changed from 140,020 to 144,716 (+4,696)
- UNASSIGNED changed from 819,467 to 814,664 (-4,803)
- CONTEXTJ did not change, at 2
- CONTEXTO did not change, at 25
- DISALLOWED did not change, at 140,449
- ID_DIS or FREE_PVAL changed from 14,149 to 14,256 (+107)
- TOTAL did not change, at 1,114,112

Code points that changed derived property value from other than UNASSIGNED: 0

There are no changes made to Unicode between version 16.0.0 and 17.0.0 that impact PRECIS calculation of the derived property values.

<!-- END of REPORT.md embed -->

## Usage

1. Install [Babashka](https://github.com/babashka/babashka#installation)

    ```
    $ bb tasks
    The following tasks are available:

    download     Download Unicode data files, IANA tables, and other required reference sources
    extract      Extract reference datasets from T. Nemoto draft and IANA PRECIS tables
    changes      Generate PRECIS change tables and verification data using RFC 8264 algorithm
    verify       Verify generated tables match extracted reference datasets
    report       Generate analysis report of PRECIS property changes across Unicode versions
    codepoint    Analyze and diagnose PRECIS classification for a specific codepoint across Unicode versions
    createtables Generate pristine PRECIS datasets for specified Unicode versions
    clean        Remove all generated directories (reference/, tables/)
    distclean    Remove all generated directories and downloaded Unicode data
    ```

2. Generate and validate datasets:
    ```bash
    # Download data from the unicode Consortium
    bb download

    # Extract reference data
    bb extract

    # Generate change analysis and verification datasets
    bb changes

    # Verify against reference
    bb verify

    # Update the report
    bb report

    # Get a report on a specific codepoint
    bb codepoint <codepoint>
    # Example
    bb codepoint 111C9

    # Calculate derived property values (into tables/)
    bb createtables # all version
    bb createtables 11.0.0
    ```

## Project Structure

- `scripts/` - Data generation and validation scripts
- `reference/` - Reference and verification data
  - `tables-extracted/` - Reference datasets from [[T. Nemoto draft]](#NEMOTO-DRAFT) and [[IANA's precis-tables]](#IANA-PRECIS)
  - `tables-generated/` - Generated datasets for verification against extracted references
- `data/` - Unicode data files from UCD
  - `[version]/` - Per-Unicode version (e.g., 6.3.0, 17.0.0)
    - Unicode source files (UnicodeData.txt, DerivedCoreProperties.txt, etc.)
- `tables/` - Unicode data files and generated PRECIS datasets
  - `[version]/` - Per-Unicode version (e.g., 6.3.0, 17.0.0)
    - allcodepoints.txt, byscript.html, bygc.html, xmlrfc.xml, idnabis-tables.xml, iana.csv

## Generated Dataset Formats

The PRECIS derived property values are generated for each Unicode version in multiple formats into the `tables/[version]/` dir

### Derived Property Values

Codepoints can have the following derived property values:

- **PVALID**: The code point is allowed in any PRECIS string class
- **ID_DIS or FREE_PVAL**: The code point is disallowed in the IdentifierClass but allowed in the FreeformClass
- **DISALLOWED**: The code point is disallowed in all PRECIS string classes
- **UNASSIGNED**: The code point is not assigned by Unicode Consortium and cannot be used in PRECIS
- **CONTEXTJ**: The code point must be checked according to contextual rules for join controls
- **CONTEXTO**: The code point must be checked according to contextual rules for other code points than join controls

### Output Files

#### `allcodepoints.txt`
One line per codepoint with four semicolon-separated fields:
1. Codepoint in hex
2. Derived property value
3. Rules in PRECIS that match
4. Name of character

Example:
```
003F;DISALLOWED;;QUESTION MARK
0040;DISALLOWED;;COMMERCIAL AT
0041;ID_DIS;AB;LATIN CAPITAL LETTER A
0042;ID_DIS;AB;LATIN CAPITAL LETTER B
```

### `derived-props-M.m.txt`

The format used by the python precis implementation [precis_i18n](https://github.com/byllyfish/precis_i18n).
It is notable because it shows the corresponding Section 8 rule that determined the property value.

Example:

```
0000-001F DISALLOWED/controls
0020-0020 FREE_PVAL/spaces
0021-007E PVALID/ascii7
007F-009F DISALLOWED/controls
00A0-00A0 FREE_PVAL/has_compat
```



#### `byscript.html` and `bygc.html`
HTML tables with code points sorted by script or general category, containing:
1. Codepoint in hex
2. Character itself
3. Derived property value
4. Rules in PRECIS that match
5. General Category (Gc) value
6. Name of character

#### `xmlrfc.xml`
All code points in Unicode Character Database (UCD) format:
```
0000..002C  ; DISALLOWED  # <control>..COMMA
002D        ; PVALID      # HYPHEN-MINUS
002E..002F  ; DISALLOWED  # FULL STOP..SOLIDUS
0030..0039  ; PVALID      # DIGIT ZERO..DIGIT NINE
```

#### `idnabis-tables.xml`
All code points in the XML format IANA uses:
```xml
<record>
  <codepoint>018C-018D</codepoint>
  <property>PVALID</property>
  <description>LATIN SMALL LETTER D WITH TOPBAR..LATIN SMALL LETTER TURNED DELTA</description>
</record>
```

#### `iana.csv`
All code points in CSV format that IANA uses:
```
0000-002C,DISALLOWED,NULL..COMMA
002D,PVALID,HYPHEN-MINUS
002E-002F,DISALLOWED,FULL STOP..SOLIDUS
0030-0039,PVALID,DIGIT ZERO..DIGIT NINE
```

## References

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

<a id="L2-17-247">[L2/17-247]</a>  
Whistler, K. and L. Iancu (2017).  
Proposed Property Changes for U+111C9 SHARADA SANDHI MARK.  
Unicode Technical Committee Document L2/17-247.  
<https://www.unicode.org/L2/L2017/17247-sharada-sandhi-prop-chg.txt>

<a id="L2-18-272">[L2/18-272]</a>  
Unicode Technical Committee (2018).  
Approved Minutes of UTC Meeting 157.  
Unicode Technical Committee Document L2/18-272.  
<https://www.unicode.org/L2/L2018/18272.htm>

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

