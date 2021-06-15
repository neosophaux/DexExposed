# DexExposed
A program to flip every private, protected and package-private access flag to public in an Android dex file!

## Building
Simply run `gradle makeJar`. Built with Gradle 7.0.

## Usage
```
Usage: java -jar dxp.jar [options] INPUT FILE(S)
  Options:
    -p, --do-pkg
      Only run the tool on a specific package. May be a regex that matches the
      BINARY version of a package. For example, 'Lex/pkg/[a-f0-9]{32}/data'.
      Default: .* (everything)
    -h, --help
      Prints the usage information.
      Default: false
    -o, --output-dir
      Directory to output to.
      Default: <Directory the jar is in>
```

## Note
This processes the following:
  * `Class signatures`
  * `Field signatures`
  * `Method signatures`

If there is something missing from this list, please let me know!

## Reasoning
While instrumenting Android malware, I often want to write 'plugins' in Java to interact with it. The plugins are written using the malware's own code as an API and this allows me to achieve Xposed-like functionality. In many cases though, I find that it's near impossible to do this as many items are private, protected, or package-private. It's annoying, so I created this tool to save me the headache of having to manually edit the items I needed to be public.

## dexlib2
This program is powered by [dexlib2](https://github.com/JesusFreke/smali/tree/master/dexlib2), which has the following license:
```
Copyright 2012, Google Inc.
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

   *  Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
   *  Redistributions in binary form must reproduce the above
copyright notice, this list of conditions and the following disclaimer
in the documentation and/or other materials provided with the
distribution.
   *  Neither the name of Google Inc. nor the names of its
contributors may be used to endorse or promote products derived from
this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```
