import os

def list_structure(root_dir, prefix=''):
    entries = sorted(os.listdir(root_dir))
    for i, entry in enumerate(entries):
        path = os.path.join(root_dir, entry)
        connector = '└── ' if i == len(entries) - 1 else '├── '
        print(prefix + connector + entry)
        if os.path.isdir(path):
            extension = '    ' if i == len(entries) - 1 else '│   '
            list_structure(path, prefix + extension)

# Replace with your decompiled source folder path
root = os.getcwd();
list_structure(root)
