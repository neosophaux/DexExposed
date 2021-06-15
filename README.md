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
