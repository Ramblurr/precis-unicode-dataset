## PRECIS Derived Data

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

#### `derived-props-M.m.txt`

The format used by the python precis implementation precis_i18n [[PYTHON-PRECIS](#PYTHON-PRECIS)].
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
