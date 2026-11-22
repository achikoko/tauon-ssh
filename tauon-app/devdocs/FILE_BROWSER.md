# File browser: known bugs

Bugs found by reading the code of `tauon.app.ui.containers.session.pages.files` (and the helpers it calls),
collected to be fixed one by one (navigation/view by hand, operations/transfers with a review agent; the high ones were re-checked). Line numbers are as of commit `aec201e` on `develop`.
Unimplemented features are listed apart at the end, to be done after the bugs.

Severity: **high** = data loss, crash, or a main action is broken; **medium** = wrong behaviour a user notices;
**low** = edge case or cosmetic.

## Bugs

### Navigation and history

The history is a pair of stacks (`NavigationHistory`); every navigation must push the *current* folder onto
"back" exactly once, clear "forward", and only after the new folder is listed. None of the call sites agreed on it.
Fixed: every navigation goes through `AbstractFileBrowserView.navigate` (or Back/Forward), which updates `path`,
the address bar and `NavigationHistory` on the EDT only after the folder was listed (`NavigationHistoryTest`).

- [x] **N1 — high — Up and Home push the current folder twice.** `AbstractFileBrowserView` `upAction` (line 73)
  and `btnHome` (line 109) call `addBack(path)`, and then `up()`/`home()` of both views call `addBack(path)` again
  (`LocalFileBrowserView.java:145,151`, `SshFileBrowserView.java:173,179`). After Up, the first Back "does nothing"
  (it goes to the folder already shown) and a second Back is needed.
- [x] **N2 — high — Opening a folder pushes the destination, not the current folder.** Double-click in the table
  and in the list view calls `listener.addBack(fileInfo.getPath())` (`FolderView.java:255,306`). Back after
  entering a folder stays in that folder, and the folder you came from is never in the history.
- [x] **N3 — high — Up at `/` on a remote browser jumps to the home folder.** `PathUtils.getParent("/")` returns
  `null` (`PathUtils.java:41`) and `SshFileBrowserView.render(null)` means "home" (`SshFileBrowserView.java:138`).
- [x] **N4 — medium — Up at the root adds a history entry anyway.** `upAction` pushes before `up()` checks there is
  a parent; locally `new File("/").getParent()` is `null`, so nothing is rendered but Back is enabled with the same
  folder.
- [x] **N5 — medium — Forward history is never cleared.** No navigation (address bar, Up, Home, double-click,
  bookmark) clears the forward stack, so Forward goes to unrelated folders after a new navigation.
- [x] **N6 — medium — Some navigations are not recorded at all.** Enter on a folder (`FolderView.java:217`) and
  choosing a bookmark (`OverflowMenuHandler.java:69`) call `render` without `addBack`.
- [x] **N7 — medium — History and `path` change before the folder is listed.** `addBack` happens before `render`,
  and `render` sets `this.path` immediately (`LocalFileBrowserView.java:115`, `SshFileBrowserView.java:134`);
  if listing fails, `path`, the address bar and the history disagree. Locally a failed listing is not even
  reported (see L2).
- [x] **N8 — low — `path` is written from the background thread.** `SshFileBrowserView.render(null)` sets
  `this.path = sshfs.getHome()` in the SSH worker (lines 139, 145) while the EDT reads it for `addBack`; clicking a
  navigation button before home is resolved pushes `null` into the history (Back to `null` then goes home).
- [x] **N9 — low — `addNext(String path)` ignores its argument** and pushes `this.path`
  (`AbstractFileBrowserView.java:234`). Same result today, but misleading.
- [x] **N10 — low — `PathUtils.isSamePath` is asymmetric**: `("/a", "/a/")` is the same path, `("/a/", "/a")` is
  not (`PathUtils.java:60`). The address bar uses it to skip re-navigating to the current folder.

### Address bar

- [ ] **A1 — medium — Picking a previous path from the address bar dropdown does nothing.** The combo listener only
  navigates on `"comboBoxEdited"` (Enter in the editor), `AddressBar.java:60`; selecting an item from the list fires
  `"comboBoxChanged"`. The dropdown only ever contains typed paths, never visited ones.
- [ ] **A2 — low — `AddressBar.getText()` returns `""` in path-bar mode**: `AddressBarBreadCrumbs.getSelectedText()`
  is hard-coded to `""` (line 111 of that file).
- [ ] **A3 — low — Windows: the drive breadcrumb navigates to `C:` instead of `C:\`** (`AddressBarBreadCrumbs.calculatePath`
  joins segments without a trailing separator), which Windows resolves to the drive's current directory.

### Listing and view

- [ ] **L1 — high — Sorting a local folder by Owner throws `NullPointerException`.** Local `FileInfo`s are created
  with an empty `extra` (`LocalFileSystem.java:67`), so `FileInfo.user` stays `null` (`FileInfo.java:41`) and the
  owner comparator calls `s1.compareTo(s2)` on it (`FolderView.java:200`).
- [ ] **L2 — medium — An unreadable local folder shows as empty, with no error.** `LocalFileSystem.list`
  returns an empty list when `listFiles()` returns `null` (line 85). A missing path or a file is now reported by
  `LocalFileBrowserView.listFolder`; a folder without read permission still lists as empty.
- [ ] **L3 — high — List (icon) view acts on the wrong selection.** `FolderView.getSelectedFiles()` reads the
  *table* selection (line 363) even when the list view is shown; the list's context menu calls `selectRow(e)`
  (the table method, with list coordinates, line 313). Menu actions in list view use a stale/empty selection.
  Enter (line 208) and drag-and-drop (`setFolderViewTransferHandler`, line 413) are only installed on the table.
- [ ] **L4 — medium — Ctrl+H before the first listing throws `NullPointerException`.** `FolderView.applyFilter`
  iterates `this.files`, which is `null` until `setItems` is called (lines 41, 425); also hit when the first
  listing failed.
- [ ] **L5 — medium — "Open in new tab" in the local browser opens the site's local folder, not the chosen one.**
  `LocalFileBrowserView` gives `info.getLocalFolder()` precedence over `initialPath` (lines 43-47); the remote view
  does the opposite. Only the first local tab should start in the site's folder.
- [ ] **L6 — low — The list view ignores the sort order** (the `JList` uses the model directly, the sorter only
  applies to the table).
- [x] **L7 — low — Local status text says "remote file(s)"** (`LocalFileBrowserView.java:125`); it is never shown
  anyway (see U1).

### Drag and drop

- [x] **D1 — high — Dragging files between two local tabs starts an SFTP download of local paths.**
  `LocalFileBrowserView.handleDrop` forwards any drop from another view to `FileBrowser.handleLocalDrop`, which calls
  `downloadInBackground(transferData.getFiles(), ...)` whenever the source is not `null` (`FileBrowser.java:217`),
  without checking that the source is a remote view.
- [ ] **D2 — medium — Dropping files from the OS file manager onto a local browser silently does nothing** but
  reports success (`FileBrowser.java:209-212`, `return true`).

### File operations and transfers

Paths below are relative to `ui/containers/session/pages/files/`.

- [x] **O0 — high — Paste never worked.** The system clipboard returns a deserialized `DndTransferData`, whose
  views are `transient`: `getSource()` was always `null`, so `SshFileBrowserView.handleDrop` took the remote files for
  local ones and tried to upload them. Paste now looks the views up by UUID like drag and drop does
  (`DndTransferHandler.resolveViews`).
- [x] **O1 — high — File names go into remote shell commands without escaping.** Paths are only wrapped in `"…"`
  (`ssh/SshFileOperations.java:37,82-88,166-172,256,307,380,453`, `ssh/ArchiveOperation.java:27-48,142,152`,
  `ssh/PropertiesDialog.java:400,445`, `ssh/SshMenuHandler.java:710,714,723`,
  `remote2remote/Remote2RemoteTransferDialog.java:232-241`; the sftp heredoc `<<EOF` is unquoted too). Deleting a
  file named `a" ~ "b` runs `rm -rf "/dir/a" ~ "b"` (deletes home); a name containing `$(cmd)` runs `cmd`.
  Fixed with `ShellUtils.quote` at every call site (`ShellUtilsTest`, `SshFileOperationsScriptTest`).
- [x] **O2 — high — Cut/copy in one session and paste or drop into another runs the operation on the wrong
  server.** `SshFileBrowserView.handleDrop` treats any remote source as its own file system (lines 205-217); reached
  from paste (`ssh/SshMenuHandler.java:614-620`) and DnD (`transfer/DndTransferHandler.java:201-213`). Cutting
  `/etc/app.conf` on A and pasting on B moves B's `/etc/app.conf`. Dropping into a local view downloads with the
  target session's connection as well.
- [x] **O3 — high — With sudo, only the first command of a multi-file move/copy runs as root.** Commands are joined
  as `mv a b; mv c d;` (`ssh/SshFileOperations.java:106-110,193-197`) and `SSHCommandRunner` prefixes the whole
  string with `sudo -S …` (`tauon/app/ssh/SSHCommandRunner.java:149`); the rest run as the user and fail silently.
- [x] **O4 — medium — A multi-file move/copy only checks the last command's exit status**
  (`ssh/SshFileOperations.java:74-93,158-177`): four failures followed by one success is reported as success, with no
  sudo offer.
- [ ] **O5 — medium — "Overwrite" on a folder nests it** (`target/conf/conf`): folders use `mv`/`cp -rf` without
  `-T` (`ssh/SshFileOperations.java:76-78,160-162`).
- [ ] **O6 — medium — Properties on several files sets them all to exactly the ticked boxes, starting from 000.**
  `setMultipleDetails` does not load the current mode (`ssh/PropertiesDialog.java:269-301,194-196,519-520`); and
  `getPermissions` sends only 9 bits, so setuid/setgid/sticky are cleared on any chmod.
- [ ] **O7 — medium — Create Archive with an edited target folder produces an empty archive and reports success.**
  File names are relative to the current folder but the command `cd`s to the target first, `;` instead of `&&`
  after `cd`, and the `tar | gzip` pipeline returns gzip's status (`ssh/ArchiveOperation.java:141-159`,
  `ssh/SshMenuHandler.java:340-344`).
- [ ] **O8 — medium — "Run in background" blocks the session.** `nohup "file" &` without redirecting output keeps
  the exec channel open and `SSHCommandRunner.run` joins it (`ssh/SshMenuHandler.java:717-731`); the spinner stays
  until the script ends.
- [x] **O9 — medium — Remote-to-remote transfer ignores the saved port**: `sftp user@host` without `-P`
  (`remote2remote/Remote2RemoteTransferDialog.java:232`).
- [ ] **O10 — medium — Adding/removing a remote-to-remote host while the search filter is active erases the hidden
  hosts from disk**: `save()` writes the filtered model (`remote2remote/Remote2RemoteTransferDialog.java:179-186,249-258`).
- [ ] **O11 — medium — Dialogs are shown off the EDT.** Everything run through `submitSSHOperation`/`submitLocalOperation`
  builds `JOptionPane`s and fields in the executor: duplicate/sudo/new file/new folder/link prompts
  (`ssh/SshFileOperations.java:61-67,101,326,398,484`), `ssh/ArchiveOperation.java:128-138`,
  `ssh/SshMenuHandler.java:739,751`, `ssh/PropertiesDialog.java:422,467,527`, `local/LocalFileOperations.java:25,36,43,54`.
- [ ] **O12 — medium — Upload prepares the transfer on the EDT.** `FileBrowser.uploadInBackground` uses the
  synchronous `runSSHOperation` (`FileBrowser.java:307`, from `ssh/SshMenuHandler.java:794`); uploading a big folder
  or reconnecting freezes the window.
- [ ] **O13 — medium — An IOException re-runs the whole operation.** `SessionContentPanel` catches
  `RealIOException`/`NotConnected` and loops (`tauon/app/ui/containers/session/SessionContentPanel.java:304-307`):
  a dropped channel during a move/rename re-prompts and re-runs `mv`; a persistent error loops forever.
- [ ] **O14 — low — Edit Server in the remote-to-remote dialog doesn't refresh the list** (entry changed in place,
  model not notified; `remote2remote/Remote2RemoteTransferDialog.java:103-115`).
- [ ] **O15 — low — Extract overwrites existing files without asking** (`unzip -o`, `> target` for single
  `.gz/.xz/.bz2`; `ssh/ArchiveOperation.java:38-41`).
- [ ] **O16 — low — "File not found" on delete is reported as "Access denied, delete using sudo?"**
  (`ssh/SshFileOperations.java:287`).
- [ ] **O17 — low — Failures are silent**: sudo failure dialogs are commented out
  (`ssh/SshFileOperations.java:112-117,199-204,265-270,388-393,463-466`); `newFile`/`newFolder` swallow other
  exceptions (`:368,441`), `createLink` swallows everything (`:504`).
- [ ] **O18 — low — Local delete/new folder/rename ignore failures** (`File.delete()`/`mkdirs()` results ignored in
  `tauon/app/ssh/filesystem/LocalFileSystem.java:126-138,143`; rename only prints, `local/LocalFileOperations.java:14-22`).
- [ ] **O19 — low — Cut + paste leaves the data on the clipboard**; a second paste tries to move files that are gone
  (`ssh/SshMenuHandler.java:372-394,614-620`).
- [ ] **O20 — low — F2 with nothing selected throws `ArrayIndexOutOfBoundsException`**
  (`getSelectedFiles()[0]`, `ssh/SshMenuHandler.java:182`; same pattern for Enter/open at `:99`).
- [ ] **O21 — low — Archive menu entries are case-sensitive**: `X.ZIP`/`B.TAR.GZ` get no Extract entries
  (`ssh/ArchiveOperation.java:51-66`).
- [ ] **O22 — low — Windows drag-out leaves a temp folder per drag** (`transfer/DndTransferHandler.java:60`); its
  download callback shows a dialog from the file-monitor thread (`:63-69`).

## Unimplemented (later)

- [ ] **U1 — Status bar.** `FileBrowser.updateRemoteStatus` is empty (`lblStat1`/`statusBox` are never created), so
  "N of M selected" and "Total N file(s)" are computed and dropped.
- [ ] **U2 — Move files within the same local browser** by drag and drop: `LocalFileBrowserView.handleDrop` returns
  `false` (`// TODO handle move files inside same browser`).
- [ ] **U3 — `openApp` in the local browser** (double-click on a local file) is empty.
- [ ] **U4 — `SshFileBrowserView.openDefaultAction` / `openNewTab`** are empty, unused stubs.
- [ ] **U5 — Remote external editor errors** are only printed (`SshFileBrowserView.openApp`, `// TODO handle exception`).
- [ ] **U6 — i18n**: "Local file browser"/"Remote file browser" menu items, "Move/copy files?", "Source and target
  directory is same!", "ROOT" are hard-coded.
- [ ] **U7 — Paste of files copied in the OS file manager** (`javaFileListFlavor`) is not handled, though Paste is
  offered (`ssh/SshMenuHandler.java:627-634`).
- [ ] **U8 — "Send to → this computer"** does nothing: `remote2remote/LocalPipeTransfer.java` body is commented out
  (`ssh/SshMenuHandler.java:146-149,814-817`).
- [ ] **U9 — Drag remote files out to the OS on Linux/macOS** (`transfer/DndTransferHandler.java:75-81`, comment only).
- [ ] **U10 — Local context menu**: Copy/Cut/Paste listeners empty and not added (`local/LocalMenuHandler.java:132-151`);
  "Open" shown but `open()` empty (`:196-203`); "Bookmark" always shown but `addToFavourites()` throws
  `UnsupportedOperationException` (`:268-284`); address popup "Open in new tab"/"Open in terminal" empty (`:297-303`)
  and its "Bookmark" uses a null site id (`:312`).
- [ ] **U11 — Hard links**: the Create Link checkbox reaches `SshFileSystem.createLink`, which throws
  `NotImplemented` (swallowed); `LocalFileSystem.createLink` and `chmod` do nothing.
- [ ] **U12 — chmod progress**: dialog says "Calculating...", its cancel flag is never checked, and the list is not
  refreshed afterwards (`ssh/PropertiesDialog.java:500-513`).
- [ ] **U13 — Leftovers**: empty first "Run in terminal" item overwritten later (`ssh/SshMenuHandler.java:154-157,169`);
  empty `openNewTab` branch for files (`:542-544`); external editor errors only printed (`:103-106,125-127,804-806`);
  `transfer/FileTransfer.java` entirely commented out; "TODO use guiHandle" throughout `SshFileOperations`.
- [ ] **U14 — Copy/move between servers** (wanted): pasting or dropping files from another session's remote browser
  into a remote browser is refused for now (O2); copy them through this computer (download to a temp folder, then
  upload) or with a direct remote-to-remote pipe.
