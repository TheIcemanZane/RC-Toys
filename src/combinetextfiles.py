import os

# Extensions you want to include
TEXT_EXTENSIONS = {
    ".txt", ".java", ".md", ".json", ".xml", ".csv",
    ".yml", ".yaml", ".properties", ".log", ".ini"
}

SEPARATOR = "\n-------------------------------------------\n"

output_file = "combined.txt"

def is_text_file(filename):
    _, ext = os.path.splitext(filename)
    return ext.lower() in TEXT_EXTENSIONS

def main():
    with open(output_file, "w", encoding="utf-8", errors="ignore") as outfile:
        first = True

        for root, _, files in os.walk("."):
            for file in files:
                if file == output_file:
                    continue

                if is_text_file(file):
                    path = os.path.join(root, file)

                    try:
                        with open(path, "r", encoding="utf-8", errors="ignore") as infile:
                            if not first:
                                outfile.write(SEPARATOR)
                            first = False

                            outfile.write(f"FILE: {path}\n\n")
                            outfile.write(infile.read())

                    except Exception as e:
                        print(f"Skipped {path}: {e}")

    print("Done. Output saved as combined.txt")

if __name__ == "__main__":
    main()