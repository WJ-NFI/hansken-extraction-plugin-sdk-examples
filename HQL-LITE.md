## HQL-Lite

####What is HQL-Lite?
HQL-Lite is a subset of HQL designed towards writing Matchers for Hansken External Plugins in HQL-like syntax.

######What is a matcher?
A matcher is a filter that decides whether an incoming Trace can be processed by an Extraction Plugin.

####Difference between HQL-Lite vs HQL
HQL-Lite is both a subset and expansion/extension of HQL. Meaning that it supports a small part of the HQL syntax, with 
some additional HQL-Lite unique syntax that isn't supported by HQL.

But even though the syntax looks the same, the meaning can sometimes differ.

The supported Query types are listed below.

#####Supported matchers:
|Matcher    |HQL            |HQL-Lite           |remarks                                  |
|---        |---            |---                |---                                      |
|All        |full support   |full support       |                                         |
|And        |full support   |full support       |                                         |
|None       |full support   |full support       |                                         |
|Not        |full support   |full support       |                                         |
|Range      |full support   |subset supported   |this subset only supports number ranges  |
|Or         |full support   |full support       |                                         |
|Regex      |full support   |no support         |                                         |
|Phrase     |full support   |no support         |                                         |
|HasTrace   |full support   |no support         |                                         |
|Nested     |full support   |no support         |                                         |
|Data       |no support     |full support       |`$data.something=` activates this matcher|
|DataType   |no support     |full support       |`$data.type=` activates this matcher     |
|Types      |no support     |full support       |`type=` activates this matcher           |


#####Examples:
- AllQuery
    - `""`: an empty string means match against ANY value
- AndQuery
    - `A AND B AND C`: returns `true` only if ALL arguments are `true`  
- NoneQuery
- NotQuery
    - `!A`: returns `true` only if `A` is `false`. alternate syntax is `-A`  
- NumberRangeQuery - a subset of the HQL RangeQuery that only supports numbers
- OrQuery
    - `A AND B AND C`: returns `true` only if ANY of the arguments are `true`  
- TermQuery
    - `A=hello`: returns `true` if the property `A` has as value `hello`. 
    - Term queries support:
        - Wilcards
        - Exact matching(string literals)
    
        Example: `A=?el*` means:
        -  the question mark is a wildcard for a single character
        -  the asterisk is a wildcard for 0 or more characters
        
        So `A=?el*` translates to, `A` equals a string that starts with any character, followed by `el`, followed by any
         number of characters.
        
        Meaning if property `A` has a value `eel` it will return `true`. 
        
        Other positive matches are `hell`, `mellow`, `melbourne`.
        
        Some negative matches are `heel`, `keel`, 'elmo', `a hello`.
        
        Example: `'A=?el*'` means: that this will only match if field `A` has an EXACT value of `?el*`.
- DataQuery
    - `$data.mimeClass=html`: a data query matches a property against the current **datastream** type variant of said
    property. meaning that if the current datastream is of type `raw`, then the query becomes `$data.raw.mimeClass=html`.
    - Data queries support:
        - Wilcards
        - Exact matching(string literals)
        - Number range matching  
- DataTypeQuery
    - `$data.type=raw`: a dataType query matches a property against the current **datastream** type.
    - DataType queries support:
        - Wilcards
        - Exact matching(string literals)
- TypeQuery
    - `type=data,r*w,'text'`: a types query matches against the `types` of a trace. a trace can have multiple types, 
    so this query also supports multiple values.
    - Type queries support:
        - Wilcards
        - Exact matching(string literals)
        

####How to use HQL-Lite for a matcher
#####FireFli HQL Matcher Example
```
 data.raw.size>0 AND 
-type=unallocated AND
-data.raw.mimeClass=* AND
-data.raw.mimeType=* AND
-data.raw.fileType=*
```
- `data.raw.size>0`: here we use the RangeMatcher to check that the property `data.raw.size` has a value larger than `0`
. In reality this means that this should be a Trace with a data stream, and **not** just a Trace with **just** Meta 
information. 
- `-type=unallocated`:this line contains 2 matchers; a NotMatcher, and a TermMatcher. The NotMatcher simply **negates** 
the result of the TermMatcher. And the TermMatcher dicatates that the Trace should have a `type` of `unallocated`(note: 
that Traces can have multiple types). So bottom line says we want Traces `that are NOT unallocated`.
- `-data.raw.mimeClass=*`: 2 matchers again with a wildcard, which translates to only Traces 
`with an empty data.raw.mimeClass` are allowed.   
- `-data.raw.mimeType=*`: same as the one above, which translates to only Traces `with an empty data.raw.mimeType` are 
allowed.   
- `-data.raw.fileType=*`: same again, which translates to only Traces `with an empty data.raw.fileType` are allowed.  
- all the `AND` in the syntax means that ALL these conditions have to be true to proceed.

This all makes sense, when we know that FireFli augments adds `mimeClass`, `mimeType`, `fileType` & `type` properties to
 a Trace. FireFli does this based on identifier bytes in the `data-stream`, which is why it can't be empty. And FireFli 
 can only identify data files, so `unallocated` space is out of the equation.
 
#####FireFli HQL-Lite Matcher Example
```
 $data.size>0 AND 
-type=unallocated AND
-$data.mimeClass=* AND
-$data.mimeType=* AND
-$data.fileType=*
```
With the exception of `$data`, this matcher produces the exact some result as the HQL variant.

The reason `$data.` behaves differently, is because `$data.*` isn't supported in HQL, and was supported through
a very verbose workaround.c