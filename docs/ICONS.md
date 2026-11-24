# Icons and images

Every icon and image in the app, where it came from, and every place the
app uses it. Written by `scripts/icon-index.py`; `IconIndexTest` fails
when this is out of step with the code. To change an icon everywhere,
change its file (one place); to swap which icon a place shows, the
places are listed below. Line numbers drift: re-run the script after
moving code about.

    python3 -I scripts/icon-index.py <@phosphor-icons/core 2.1.1> <@phosphor-icons/core 1.4.9>

## Icons (app/src/main/java/com/saulhdev/feeder/ui/icons/phosphor)

Phosphor Icons are MIT-licensed (see ATTRIBUTION.md). The source column
compares each drawing number for number with Phosphor's, in every weight.

### Phosphor.Archive
- File: `Archive.kt`
- Source: Phosphor, redrawn: named after a Phosphor icon but not an exact copy of 1.4.x or 2.1.1
- Used in (2):
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/BackupPage.kt:361`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SettingsSection.kt:81`

### Phosphor.ArrowCounterClockwise
- File: `ArrowCounterClockwise.kt`
- Source: Phosphor, redrawn: named after a Phosphor icon but not an exact copy of 1.4.x or 2.1.1
- Used in (7):
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SourceListPage.kt:932`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/BackupPage.kt:380`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SuggestionsPage.kt:120`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/LearnedPage.kt:182`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/BrokenFeedsPage.kt:281`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:656`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:768`

### Phosphor.ArrowLeft
- File: `ArrowLeft.kt`
- Source: Phosphor, redrawn: named after a Phosphor icon but not an exact copy of 1.4.x or 2.1.1
- Used in (5):
  - `app/src/main/java/com/saulhdev/feeder/ui/components/FeedSearchBar.kt:100`
  - `app/src/main/java/com/saulhdev/feeder/ui/components/SourceFilterBar.kt:84`
  - `app/src/main/java/com/saulhdev/feeder/ui/components/ViewWithActionBar.kt:111`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SourceListPage.kt:426`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/LearnedPage.kt:105`

### Phosphor.ArrowSquareOut
- File: `ArrowSquareOut.kt`
- Source: Phosphor, redrawn: named after a Phosphor icon but not an exact copy of 1.4.x or 2.1.1
- Used in (3):
  - `app/src/main/java/com/saulhdev/feeder/ui/components/PictureViewer.kt:251`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SourceEditPage.kt:462`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/ArticlePage.kt:241`

### Phosphor.ArrowUUpLeft
- File: `ArrowUUpLeft.kt`
- Source: Phosphor 1.4.9 arrow-u-up-left-duotone (duotone), exact
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SortFilterSheet.kt:142`

### Phosphor.ArrowsClockwise
- File: `ArrowsClockwise.kt`
- Source: Phosphor, redrawn: named after a Phosphor icon but not an exact copy of 1.4.x or 2.1.1
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SettingsSection.kt:80`

### Phosphor.ArrowsDownUp
- File: `ArrowsDownUp.kt`
- Source: Phosphor 2.1.1 arrows-down-up (regular), exact
- Used in (4):
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SourceListPage.kt:474`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SortFilterSheet.kt:208`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:1277`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:1286`

### Phosphor.Article
- File: `Article.kt`
- Source: Phosphor, redrawn: named after a Phosphor icon but not an exact copy of 1.4.x or 2.1.1
- Used in (3):
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SourceEditPage.kt:459`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SortFilterSheet.kt:235`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:238`

### Phosphor.Asterisk
- File: `Asterisk.kt`
- Source: Phosphor 1.4.9 asterisk-duotone (duotone), exact
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:1136`

### Phosphor.BookBookmark
- File: `BookBookmark.kt`
- Source: Phosphor, redrawn: named after a Phosphor icon but not an exact copy of 1.4.x or 2.1.1
- Used in (3):
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SourceListPage.kt:550`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:690`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:853`

### Phosphor.BookOpenUser
- File: `BookOpenUser.kt`
- Source: Phosphor, redrawn: named after a Phosphor icon but not an exact copy of 1.4.x or 2.1.1
- Used in (3):
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SettingsSection.kt:79`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:271`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:554`

### Phosphor.Bookmarks
- File: `Bookmarks.kt`
- Source: Phosphor, redrawn: named after a Phosphor icon but not an exact copy of 1.4.x or 2.1.1
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SourceListPage.kt:547`

### Phosphor.BracketsSquare
- File: `BracketsSquare.kt`
- Source: Phosphor 2.1.1 brackets-square (regular), exact
- Used in (2):
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SourceListPage.kt:922`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:733`

### Phosphor.Browser
- File: `Browser.kt`
- Source: Phosphor, redrawn: named after a Phosphor icon but not an exact copy of 1.4.x or 2.1.1
- Used in (6):
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SourceEditPage.kt:460`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SourceAddPage.kt:410`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/BrokenFeedsPage.kt:212`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/BrokenFeedsPage.kt:299`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/AboutPage.kt:472`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:344`

### Phosphor.Bug
- File: `Bug.kt`
- Source: Phosphor, redrawn: named after a Phosphor icon but not an exact copy of 1.4.x or 2.1.1
- Used in (3):
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SettingsSection.kt:83`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:1011`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:1044`

### Phosphor.CaretDown
- File: `CaretDown.kt`
- Source: Phosphor 1.4.9 caret-down (regular), exact
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/ui/components/Block.kt:131`

### Phosphor.CaretUp
- File: `CaretUp.kt`
- Source: Phosphor 1.4.9 caret-up (regular), exact
- Used in (3):
  - `app/src/main/java/com/saulhdev/feeder/ui/overlay/FeedScaffold.kt:684`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/ArticleListPage.kt:582`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:1304`

### Phosphor.ChatCircle
- File: `ChatCircle.kt`
- Source: Phosphor, redrawn: named after a Phosphor icon but not an exact copy of 1.4.x or 2.1.1
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/ArticlePage.kt:433`

### Phosphor.Check
- File: `Check.kt`
- Source: Phosphor 1.4.9 check-duotone (duotone), exact
- Used in (15):
  - `app/src/main/java/com/saulhdev/feeder/ui/overlay/CategoryChipRow.kt:123`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SourceListPage.kt:484`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SourceListPage.kt:498`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SourceListPage.kt:894`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SourceListPage.kt:998`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SourceEditPage.kt:188`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/AccountPage.kt:277`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/AccountPage.kt:363`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SourceAddPage.kt:497`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SourceAddPage.kt:621`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SourceAddPage.kt:658`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/BrokenFeedsPage.kt:245`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SortFilterSheet.kt:162`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/InsecureFeedsPage.kt:136`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/InsecureFeedsPage.kt:218`

### Phosphor.CheckCircle
- File: `CheckCircle.kt`
- Source: Phosphor, redrawn: named after a Phosphor icon but not an exact copy of 1.4.x or 2.1.1
- Used in (3):
  - `app/src/main/java/com/saulhdev/feeder/ui/components/Chip.kt:111`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SourceAddPage.kt:596`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SortFilterSheet.kt:395`

### Phosphor.Circle
- File: `Circle.kt`
- Source: Phosphor, redrawn: named after a Phosphor icon but not an exact copy of 1.4.x or 2.1.1
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/ui/components/Chip.kt:112`

### Phosphor.CircleHalf
- File: `CircleHalf.kt`
- Source: Phosphor, redrawn: named after a Phosphor icon but not an exact copy of 1.4.x or 2.1.1
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:323`

### Phosphor.Clock
- File: `Clock.kt`
- Source: Phosphor, redrawn: named after a Phosphor icon but not an exact copy of 1.4.x or 2.1.1
- Used in (5):
  - `app/src/main/java/com/saulhdev/feeder/viewmodels/SourceListViewModel.kt:669`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:252`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:315`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:899`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:908`

### Phosphor.CloudArrowDown
- File: `CloudArrowDown.kt`
- Source: Phosphor 1.4.9 cloud-arrow-down (regular), exact
- Used in (3):
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/AccountPage.kt:170`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:371`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:390`

### Phosphor.CloudArrowUp
- File: `CloudArrowUp.kt`
- Source: Phosphor, redrawn: named after a Phosphor icon but not an exact copy of 1.4.x or 2.1.1
- Used in (3):
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:511`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:528`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:537`

### Phosphor.CloudSun
- File: `CloudSun.kt`
- Source: Phosphor, redrawn: named after a Phosphor icon but not an exact copy of 1.4.x or 2.1.1
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:296`

### Phosphor.Copy
- File: `Copy.kt`
- Source: Phosphor 2.1.1 copy (regular), exact
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SourceListPage.kt:522`

### Phosphor.Copyleft
- File: `Copyleft.kt`
- Source: Phosphor, redrawn: named after a Phosphor icon but not an exact copy of 1.4.x or 2.1.1
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/ui/navigation/PageItem.kt:36`

### Phosphor.DotsThreeVertical
- File: `DotsThreeVertical.kt`
- Source: Phosphor, redrawn: named after a Phosphor icon but not an exact copy of 1.4.x or 2.1.1
- Used in (3):
  - `app/src/main/java/com/saulhdev/feeder/ui/components/OverflowMenu.kt:76`
  - `app/src/main/java/com/saulhdev/feeder/ui/overlay/ArticleMenu.kt:133`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SourceListPage.kt:505`

### Phosphor.DownloadSimple
- File: `DownloadSimple.kt`
- Source: Phosphor, redrawn: named after a Phosphor icon but not an exact copy of 1.4.x or 2.1.1
- Used in (2):
  - `app/src/main/java/com/saulhdev/feeder/ui/components/PictureViewer.kt:229`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/BackupPage.kt:394`

### Phosphor.EnvelopeSimple
- File: `EnvelopeSimple.kt`
- Source: Phosphor 2.1.1 envelope-simple (regular), exact
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/AboutPage.kt:403`

### Phosphor.EyeSlash
- File: `EyeSlash.kt`
- Source: Phosphor 1.4.9 dots-three-duotone (duotone), exact
- Used in (8):
  - `app/src/main/java/com/saulhdev/feeder/ui/overlay/ArticleMenu.kt:166`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/LearnedPage.kt:285`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/LearnedPage.kt:332`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/AboutPage.kt:462`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:473`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:813`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:827`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:1150`

### Phosphor.FileArrowDown
- File: `FileArrowDown.kt`
- Source: Phosphor 2.1.1 file-arrow-down (regular), exact
- Used in (3):
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SourceListPage.kt:541`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/BookmarkImportPage.kt:132`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:711`

### Phosphor.FileArrowUp
- File: `FileArrowUp.kt`
- Source: Phosphor 2.1.1 file-arrow-up (regular), exact
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SourceListPage.kt:544`

### Phosphor.Folder
- File: `Folder.kt`
- Source: Phosphor, redrawn: named after a Phosphor icon but not an exact copy of 1.4.x or 2.1.1
- Used in (2):
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/BackupPage.kt:350`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:490`

### Phosphor.FunnelSimple
- File: `FunnelSimple.kt`
- Source: Phosphor, redrawn: named after a Phosphor icon but not an exact copy of 1.4.x or 2.1.1
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:844`

### Phosphor.GearSix
- File: `GearSix.kt`
- Source: Phosphor, redrawn: named after a Phosphor icon but not an exact copy of 1.4.x or 2.1.1
- Used in (5):
  - `app/src/main/java/com/saulhdev/feeder/ui/components/BackgroundDataHint.kt:85`
  - `app/src/main/java/com/saulhdev/feeder/ui/overlay/FeedScaffold.kt:367`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/LauncherPage.kt:142`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SourceEditPage.kt:458`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/ArticleListPage.kt:550`

### Phosphor.GithubLogo
- File: `GithubLogo.kt`
- Source: Phosphor 1.4.9 github-logo (regular), exact
- Used in (5):
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/AboutPage.kt:409`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/AboutPage.kt:451`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:1213`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:1222`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:1230`

### Phosphor.Graph
- File: `Graph.kt`
- Source: Phosphor, redrawn: named after a Phosphor icon but not an exact copy of 1.4.x or 2.1.1
- Used in (3):
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SourceListPage.kt:525`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:646`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:680`

### Phosphor.Hash
- File: `Hash.kt`
- Source: Phosphor, redrawn: named after a Phosphor icon but not an exact copy of 1.4.x or 2.1.1
- Used in (9):
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SourceListPage.kt:511`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SourceListPage.kt:883`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SourceListPage.kt:900`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SourceListPage.kt:905`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SortFilterSheet.kt:288`
  - `app/src/main/java/com/saulhdev/feeder/viewmodels/SourceListViewModel.kt:632`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:937`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:1083`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:1128`

### Phosphor.HeartStraight
- File: `HeartStraight.kt`
- Source: Phosphor 1.4.9 heart-straight (regular), exact
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/AboutPage.kt:302`

### Phosphor.Info
- File: `Info.kt`
- Source: Phosphor, redrawn: named after a Phosphor icon but not an exact copy of 1.4.x or 2.1.1
- Used in (15):
  - `app/src/main/java/com/saulhdev/feeder/ui/overlay/ArticleMenu.kt:157`
  - `app/src/main/java/com/saulhdev/feeder/ui/navigation/NavigationManager.kt:194`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/BackupPage.kt:288`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/LauncherPage.kt:178`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SourceAddPage.kt:497`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/AboutPage.kt:467`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:590`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:598`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:614`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:629`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:948`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:1091`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:1249`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:1257`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:1294`

### Phosphor.Lifebuoy
- File: `Lifebuoy.kt`
- Source: Phosphor, redrawn: named after a Phosphor icon but not an exact copy of 1.4.x or 2.1.1
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SettingsSection.kt:82`

### Phosphor.LinkBreak
- File: `LinkBreak.kt`
- Source: Phosphor 2.1.1 link-break (regular), exact
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SourceListPage.kt:528`

### Phosphor.ListChecks
- File: `ListChecks.kt`
- Source: Phosphor, redrawn: named after a Phosphor icon but not an exact copy of 1.4.x or 2.1.1
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:785`

### Phosphor.ListDashes
- File: `ListDashes.kt`
- Source: Phosphor, redrawn: named after a Phosphor icon but not an exact copy of 1.4.x or 2.1.1
- Used in (3):
  - `app/src/main/java/com/saulhdev/feeder/ui/navigation/PageItem.kt:41`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SourceListPage.kt:927`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:211`

### Phosphor.LockSimpleOpen
- File: `LockSimpleOpen.kt`
- Source: Phosphor 2.1.1 lock-simple-open (regular), exact
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SourceListPage.kt:531`

### Phosphor.MagnifyingGlass
- File: `MagnifyingGlass.kt`
- Source: Phosphor 1.4.9 dots-three-duotone (duotone), exact
- Used in (6):
  - `app/src/main/java/com/saulhdev/feeder/ui/overlay/FeedScaffold.kt:337`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SourceEditPage.kt:321`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/ArticleListPage.kt:510`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/BookmarkImportPage.kt:160`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/InsecureFeedsPage.kt:123`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/InsecureFeedsPage.kt:246`

### Phosphor.MapPin
- File: `MapPin.kt`
- Source: Phosphor, redrawn: named after a Phosphor icon but not an exact copy of 1.4.x or 2.1.1
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:305`

### Phosphor.Megaphone
- File: `Megaphone.kt`
- Source: Phosphor, redrawn: named after a Phosphor icon but not an exact copy of 1.4.x or 2.1.1
- Used in (7):
  - `app/src/main/java/com/saulhdev/feeder/ui/overlay/ArticleCard.kt:1089`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/AboutPage.kt:417`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:446`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:455`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:978`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:987`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:1056`

### Phosphor.Moon
- File: `Moon.kt`
- Source: Phosphor, redrawn: named after a Phosphor icon but not an exact copy of 1.4.x or 2.1.1
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:172`

### Phosphor.Newspaper
- File: `Newspaper.kt`
- Source: Phosphor, redrawn: named after a Phosphor icon but not an exact copy of 1.4.x or 2.1.1
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SettingsSection.kt:78`

### Phosphor.PaintRoller
- File: `PaintRoller.kt`
- Source: Phosphor, redrawn: named after a Phosphor icon but not an exact copy of 1.4.x or 2.1.1
- Used in (2):
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SettingsSection.kt:76`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:152`

### Phosphor.PencilSimple
- File: `PencilSimple.kt`
- Source: Phosphor 2.1.1 pencil-simple (regular), exact
- Used in (2):
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/CategoryListPage.kt:137`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/BrokenFeedsPage.kt:309`

### Phosphor.Play
- File: `Play.kt`
- Source: Phosphor, redrawn: named after a Phosphor icon but not an exact copy of 1.4.x or 2.1.1
- Used in (2):
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:743`
  - `app/src/main/java/com/saulhdev/feeder/utils/HtmlToComposable.kt:1815`

### Phosphor.Plus
- File: `Plus.kt`
- Source: Phosphor 2.1.1 plus (regular), exact
- Used in (7):
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SourceListPage.kt:452`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SuggestionsPage.kt:185`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/FeedLibraryPage.kt:158`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SourceEditPage.kt:529`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/BlockedWordsPage.kt:136`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/BookmarkImportPage.kt:227`
  - `app/src/main/java/com/saulhdev/feeder/viewmodels/SourceListViewModel.kt:650`

### Phosphor.Power
- File: `Power.kt`
- Source: Phosphor, redrawn: named after a Phosphor icon but not an exact copy of 1.4.x or 2.1.1
- Used in (3):
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SourceListPage.kt:911`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/AccountPage.kt:179`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:891`

### Phosphor.Prohibit
- File: `Prohibit.kt`
- Source: Phosphor 1.4.9 dots-three-duotone (duotone), exact
- Used in (6):
  - `app/src/main/java/com/saulhdev/feeder/ui/overlay/ArticleMenu.kt:156`
  - `app/src/main/java/com/saulhdev/feeder/ui/overlay/ArticleMenu.kt:163`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SourceListPage.kt:916`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SuggestionsPage.kt:191`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:700`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:927`

### Phosphor.PushPin
- File: `PushPin.kt`
- Source: Phosphor 2.1.1 push-pin (regular), exact
- Used in (3):
  - `app/src/main/java/com/saulhdev/feeder/ui/components/SourceItem.kt:221`
  - `app/src/main/java/com/saulhdev/feeder/ui/overlay/ArticleCard.kt:1052`
  - `app/src/main/java/com/saulhdev/feeder/ui/overlay/ArticleMenu.kt:152`

### Phosphor.Rss
- File: `Rss.kt`
- Source: Phosphor, redrawn: named after a Phosphor icon but not an exact copy of 1.4.x or 2.1.1
- Used in (2):
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SortFilterSheet.kt:260`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SettingsSection.kt:77`

### Phosphor.ShareNetwork
- File: `ShareNetwork.kt`
- Source: Phosphor, redrawn: named after a Phosphor icon but not an exact copy of 1.4.x or 2.1.1
- Used in (3):
  - `app/src/main/java/com/saulhdev/feeder/ui/components/PictureViewer.kt:242`
  - `app/src/main/java/com/saulhdev/feeder/ui/overlay/ArticleMenu.kt:151`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/ArticlePage.kt:261`

### Phosphor.SlidersHorizontal
- File: `SlidersHorizontal.kt`
- Source: Phosphor, redrawn: named after a Phosphor icon but not an exact copy of 1.4.x or 2.1.1
- Used in (2):
  - `app/src/main/java/com/saulhdev/feeder/ui/overlay/FeedScaffold.kt:346`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/ArticleListPage.kt:518`

### Phosphor.SlidersHorizontalFill
- File: `SlidersHorizontalFill.kt`
- Source: Phosphor, redrawn: named after a Phosphor icon but not an exact copy of 1.4.x or 2.1.1
- Used in (2):
  - `app/src/main/java/com/saulhdev/feeder/ui/overlay/FeedScaffold.kt:346`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/ArticleListPage.kt:517`

### Phosphor.SortAscending
- File: `SortAscending.kt`
- Source: Phosphor, redrawn: named after a Phosphor icon but not an exact copy of 1.4.x or 2.1.1
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SourceListPage.kt:496`

### Phosphor.SortDescending
- File: `SortDescending.kt`
- Source: Phosphor, redrawn: named after a Phosphor icon but not an exact copy of 1.4.x or 2.1.1
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SourceListPage.kt:496`

### Phosphor.Sparkle
- File: `Sparkle.kt`
- Source: Phosphor 1.4.9 dots-three-duotone (duotone), exact
- Used in (2):
  - `app/src/main/java/com/saulhdev/feeder/ui/overlay/ArticleMenu.kt:155`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:721`

### Phosphor.SpeakerHigh
- File: `SpeakerHigh.kt`
- Source: Phosphor, redrawn: named after a Phosphor icon but not an exact copy of 1.4.x or 2.1.1
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:836`

### Phosphor.Stack
- File: `Stack.kt`
- Source: Phosphor, redrawn: named after a Phosphor icon but not an exact copy of 1.4.x or 2.1.1
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:917`

### Phosphor.Swatches
- File: `Swatches.kt`
- Source: Phosphor, redrawn: named after a Phosphor icon but not an exact copy of 1.4.x or 2.1.1
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:188`

### Phosphor.TextAa
- File: `TextAa.kt`
- Source: Phosphor, redrawn: named after a Phosphor icon but not an exact copy of 1.4.x or 2.1.1
- Used in (2):
  - `app/src/main/java/com/saulhdev/feeder/viewmodels/SourceListViewModel.kt:627`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:201`

### Phosphor.ThumbsDown
- File: `ThumbsDown.kt`
- Source: Phosphor, redrawn: named after a Phosphor icon but not an exact copy of 1.4.x or 2.1.1
- Used in (3):
  - `app/src/main/java/com/saulhdev/feeder/ui/overlay/NotInterestedSwipe.kt:144`
  - `app/src/main/java/com/saulhdev/feeder/ui/overlay/ArticleMenu.kt:165`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SortFilterSheet.kt:333`

### Phosphor.TrashSimple
- File: `TrashSimple.kt`
- Source: Phosphor 2.1.1 trash-simple (regular), exact
- Used in (7):
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SourceListPage.kt:887`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SourceEditPage.kt:180`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/LearnedPage.kt:292`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/BlockedWordsPage.kt:181`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/CategoryListPage.kt:140`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/BrokenFeedsPage.kt:316`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:671`

### Phosphor.UploadSimple
- File: `UploadSimple.kt`
- Source: Phosphor, redrawn: named after a Phosphor icon but not an exact copy of 1.4.x or 2.1.1
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/BackupPage.kt:403`

### Phosphor.UserCircle
- File: `UserCircle.kt`
- Source: Phosphor, redrawn: named after a Phosphor icon but not an exact copy of 1.4.x or 2.1.1
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:545`

### Phosphor.WifiHigh
- File: `WifiHigh.kt`
- Source: Phosphor, redrawn: named after a Phosphor icon but not an exact copy of 1.4.x or 2.1.1
- Used in (2):
  - `app/src/main/java/com/saulhdev/feeder/ui/components/SourceItem.kt:164`
  - `app/src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt:862`

### Phosphor.X
- File: `X.kt`
- Source: Phosphor, redrawn: named after a Phosphor icon but not an exact copy of 1.4.x or 2.1.1
- Used in (7):
  - `app/src/main/java/com/saulhdev/feeder/ui/components/PictureViewer.kt:219`
  - `app/src/main/java/com/saulhdev/feeder/ui/components/FeedSearchBar.kt:133`
  - `app/src/main/java/com/saulhdev/feeder/ui/components/SourceFilterBar.kt:111`
  - `app/src/main/java/com/saulhdev/feeder/ui/overlay/ActiveFilters.kt:157`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SourceListPage.kt:426`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SourceListPage.kt:607`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/LearnedPage.kt:105`

## Images (app/src/main/res)

Whisper's own artwork (brand marks, weather and Glance icons) unless the
name says otherwise. Each density folder holds a copy of the same image.

### drawable/empty_bookmarks_dark
- Files: `drawable-nodpi/empty_bookmarks_dark.png`
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/ui/components/BookmarksEmptyState.kt:61`

### drawable/empty_bookmarks_light
- Files: `drawable-nodpi/empty_bookmarks_light.png`
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/ui/components/BookmarksEmptyState.kt:62`

### drawable/empty_feed_dark
- Files: `drawable-nodpi/empty_feed_dark.png`
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/ui/components/FeedEmptyState.kt:102`

### drawable/empty_feed_light
- Files: `drawable-nodpi/empty_feed_light.png`
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/ui/components/FeedEmptyState.kt:102`

### drawable/ic_brand_mark
- Files: `drawable-hdpi/ic_brand_mark.png`, `drawable-mdpi/ic_brand_mark.png`, `drawable-xhdpi/ic_brand_mark.png`, `drawable-xxhdpi/ic_brand_mark.png`, `drawable-xxxhdpi/ic_brand_mark.png`
- Used in (2):
  - `app/src/main/java/com/saulhdev/feeder/ui/components/BrandTitle.kt:62`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SplashPage.kt:144`

### drawable/ic_glance_articles_read
- Files: `drawable-hdpi/ic_glance_articles_read.png`, `drawable-mdpi/ic_glance_articles_read.png`, `drawable-xhdpi/ic_glance_articles_read.png`, `drawable-xxhdpi/ic_glance_articles_read.png`, `drawable-xxxhdpi/ic_glance_articles_read.png`
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/ui/overlay/GlanceRow.kt:256`

### drawable/ic_glance_location
- Files: `drawable-hdpi/ic_glance_location.png`, `drawable-mdpi/ic_glance_location.png`, `drawable-xhdpi/ic_glance_location.png`, `drawable-xxhdpi/ic_glance_location.png`, `drawable-xxxhdpi/ic_glance_location.png`
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/ui/overlay/GlanceRow.kt:244`

### drawable/ic_glance_sunrise
- Files: `drawable-hdpi/ic_glance_sunrise.png`, `drawable-mdpi/ic_glance_sunrise.png`, `drawable-xhdpi/ic_glance_sunrise.png`, `drawable-xxhdpi/ic_glance_sunrise.png`, `drawable-xxxhdpi/ic_glance_sunrise.png`
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/ui/overlay/GlanceRow.kt:232`

### drawable/ic_glance_sunset
- Files: `drawable-hdpi/ic_glance_sunset.png`, `drawable-mdpi/ic_glance_sunset.png`, `drawable-xhdpi/ic_glance_sunset.png`, `drawable-xxhdpi/ic_glance_sunset.png`, `drawable-xxxhdpi/ic_glance_sunset.png`
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/ui/overlay/GlanceRow.kt:231`

### drawable/ic_glance_weather_clear
- Files: `drawable-hdpi/ic_glance_weather_clear.png`, `drawable-mdpi/ic_glance_weather_clear.png`, `drawable-xhdpi/ic_glance_weather_clear.png`, `drawable-xxhdpi/ic_glance_weather_clear.png`, `drawable-xxxhdpi/ic_glance_weather_clear.png`
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/manager/glance/WeatherRepository.kt:229`

### drawable/ic_glance_weather_clear_night
- Files: `drawable-hdpi/ic_glance_weather_clear_night.png`, `drawable-mdpi/ic_glance_weather_clear_night.png`, `drawable-xhdpi/ic_glance_weather_clear_night.png`, `drawable-xxhdpi/ic_glance_weather_clear_night.png`, `drawable-xxxhdpi/ic_glance_weather_clear_night.png`
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/manager/glance/WeatherRepository.kt:230`

### drawable/ic_glance_weather_cloudy
- Files: `drawable-hdpi/ic_glance_weather_cloudy.png`, `drawable-mdpi/ic_glance_weather_cloudy.png`, `drawable-xhdpi/ic_glance_weather_cloudy.png`, `drawable-xxhdpi/ic_glance_weather_cloudy.png`, `drawable-xxxhdpi/ic_glance_weather_cloudy.png`
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/manager/glance/WeatherRepository.kt:233`

### drawable/ic_glance_weather_fog
- Files: `drawable-hdpi/ic_glance_weather_fog.png`, `drawable-mdpi/ic_glance_weather_fog.png`, `drawable-xhdpi/ic_glance_weather_fog.png`, `drawable-xxhdpi/ic_glance_weather_fog.png`, `drawable-xxxhdpi/ic_glance_weather_fog.png`
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/manager/glance/WeatherRepository.kt:234`

### drawable/ic_glance_weather_partly_cloudy
- Files: `drawable-hdpi/ic_glance_weather_partly_cloudy.png`, `drawable-mdpi/ic_glance_weather_partly_cloudy.png`, `drawable-xhdpi/ic_glance_weather_partly_cloudy.png`, `drawable-xxhdpi/ic_glance_weather_partly_cloudy.png`, `drawable-xxxhdpi/ic_glance_weather_partly_cloudy.png`
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/manager/glance/WeatherRepository.kt:231`

### drawable/ic_glance_weather_partly_cloudy_night
- Files: `drawable-hdpi/ic_glance_weather_partly_cloudy_night.png`, `drawable-mdpi/ic_glance_weather_partly_cloudy_night.png`, `drawable-xhdpi/ic_glance_weather_partly_cloudy_night.png`, `drawable-xxhdpi/ic_glance_weather_partly_cloudy_night.png`, `drawable-xxxhdpi/ic_glance_weather_partly_cloudy_night.png`
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/manager/glance/WeatherRepository.kt:232`

### drawable/ic_glance_weather_rain
- Files: `drawable-hdpi/ic_glance_weather_rain.png`, `drawable-mdpi/ic_glance_weather_rain.png`, `drawable-xhdpi/ic_glance_weather_rain.png`, `drawable-xxhdpi/ic_glance_weather_rain.png`, `drawable-xxxhdpi/ic_glance_weather_rain.png`
- Used in (2):
  - `app/src/main/java/com/saulhdev/feeder/ui/overlay/GlanceRow.kt:408`
  - `app/src/main/java/com/saulhdev/feeder/manager/glance/WeatherRepository.kt:245`

### drawable/ic_glance_weather_rain_night
- Files: `drawable-hdpi/ic_glance_weather_rain_night.png`, `drawable-mdpi/ic_glance_weather_rain_night.png`, `drawable-xhdpi/ic_glance_weather_rain_night.png`, `drawable-xxhdpi/ic_glance_weather_rain_night.png`, `drawable-xxxhdpi/ic_glance_weather_rain_night.png`
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/manager/glance/WeatherRepository.kt:245`

### drawable/ic_glance_weather_snow
- Files: `drawable-hdpi/ic_glance_weather_snow.png`, `drawable-mdpi/ic_glance_weather_snow.png`, `drawable-xhdpi/ic_glance_weather_snow.png`, `drawable-xxhdpi/ic_glance_weather_snow.png`, `drawable-xxxhdpi/ic_glance_weather_snow.png`
- Used in (2):
  - `app/src/main/java/com/saulhdev/feeder/manager/glance/WeatherRepository.kt:237`
  - `app/src/main/java/com/saulhdev/feeder/manager/glance/WeatherRepository.kt:239`

### drawable/ic_glance_weather_storm
- Files: `drawable-hdpi/ic_glance_weather_storm.png`, `drawable-mdpi/ic_glance_weather_storm.png`, `drawable-xhdpi/ic_glance_weather_storm.png`, `drawable-xxhdpi/ic_glance_weather_storm.png`, `drawable-xxxhdpi/ic_glance_weather_storm.png`
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/manager/glance/WeatherRepository.kt:240`

### drawable/ic_glance_weather_unknown
- Files: `drawable-hdpi/ic_glance_weather_unknown.png`, `drawable-mdpi/ic_glance_weather_unknown.png`, `drawable-xhdpi/ic_glance_weather_unknown.png`, `drawable-xxhdpi/ic_glance_weather_unknown.png`, `drawable-xxxhdpi/ic_glance_weather_unknown.png`
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/manager/glance/WeatherRepository.kt:241`

### drawable/ic_notification
- Files: `drawable-hdpi/ic_notification.png`, `drawable-mdpi/ic_notification.png`, `drawable-xhdpi/ic_notification.png`, `drawable-xxhdpi/ic_notification.png`, `drawable-xxxhdpi/ic_notification.png`
- Used in (4):
  - `app/src/main/java/com/saulhdev/feeder/manager/update/UpdateCheckWorker.kt:203`
  - `app/src/main/java/com/saulhdev/feeder/manager/sync/BreakingAlert.kt:143`
  - `app/src/main/java/com/saulhdev/feeder/manager/sync/SyncWatchdog.kt:290`
  - `app/src/main/java/com/saulhdev/feeder/manager/sync/FeedSyncer.kt:400`

### drawable/ic_splash_mark
- Files: `drawable-hdpi/ic_splash_mark.png`, `drawable-mdpi/ic_splash_mark.png`, `drawable-xhdpi/ic_splash_mark.png`, `drawable-xxhdpi/ic_splash_mark.png`, `drawable-xxxhdpi/ic_splash_mark.png`
- Used in (1):
  - `app/src/main/res/values/styles.xml:63`

### drawable/ic_whisper_save
- Files: `drawable/ic_whisper_save.xml`
- Used in (5):
  - `app/src/main/java/com/saulhdev/feeder/ui/components/SaveMark.kt:78`
  - `app/src/main/java/com/saulhdev/feeder/ui/overlay/FeedScaffold.kt:352`
  - `app/src/main/java/com/saulhdev/feeder/ui/overlay/ArticleCard.kt:730`
  - `app/src/main/java/com/saulhdev/feeder/ui/overlay/ArticleMenu.kt:149`
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/ArticleListPage.kt:536`

### drawable/ic_wordmark_light
- Files: `drawable-hdpi/ic_wordmark_light.png`, `drawable-mdpi/ic_wordmark_light.png`, `drawable-xhdpi/ic_wordmark_light.png`, `drawable-xxhdpi/ic_wordmark_light.png`, `drawable-xxxhdpi/ic_wordmark_light.png`
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SplashPage.kt:154`

### drawable/ic_wordmark_navy
- Files: `drawable-hdpi/ic_wordmark_navy.png`, `drawable-mdpi/ic_wordmark_navy.png`, `drawable-xhdpi/ic_wordmark_navy.png`, `drawable-xxhdpi/ic_wordmark_navy.png`, `drawable-xxxhdpi/ic_wordmark_navy.png`
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/SplashPage.kt:155`

### drawable/ic_youtube
- Files: `drawable/ic_youtube.xml`
- Used in (5):
  - `app/src/main/java/com/saulhdev/feeder/utils/HtmlToComposable.kt:1729`
  - `app/src/main/java/com/saulhdev/feeder/utils/HtmlToComposable.kt:1730`
  - `app/src/main/java/com/saulhdev/feeder/utils/HtmlToComposable.kt:1791`
  - `app/src/main/java/com/saulhdev/feeder/utils/HtmlToComposable.kt:1792`
  - `app/src/main/java/com/saulhdev/feeder/utils/HtmlToComposable.kt:1793`

### drawable/lockup_horizontal_notag_dark
- Files: `drawable-nodpi/lockup_horizontal_notag_dark.png`
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/OnboardingPage.kt:187`

### drawable/lockup_horizontal_notag_light
- Files: `drawable-nodpi/lockup_horizontal_notag_light.png`
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/OnboardingPage.kt:188`

### drawable/nyancat_labs_symbol
- Files: `drawable-nodpi/nyancat_labs_symbol.webp`
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/AboutPage.kt:511`

### drawable/onboarding_bg_dark
- Files: `drawable-nodpi/onboarding_bg_dark.png`
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/OnboardingPage.kt:149`

### drawable/onboarding_bg_light
- Files: `drawable-nodpi/onboarding_bg_light.png`
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/OnboardingPage.kt:149`

### drawable/placeholder_article_dark
- Files: `drawable-nodpi/placeholder_article_dark.png`
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/ui/overlay/ArticleCard.kt:997`

### drawable/placeholder_article_light
- Files: `drawable-nodpi/placeholder_article_light.png`
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/ui/overlay/ArticleCard.kt:998`

### drawable/placeholder_image_article_day
- Files: `drawable/placeholder_image_article_day.xml`
- Used in (1):
  - `app/src/main/java/com/saulhdev/feeder/utils/HtmlToComposable.kt:1948`

### mipmap/ic_launcher
- Files: `mipmap-anydpi/ic_launcher.xml`
- Used in (2):
  - `app/src/main/java/com/saulhdev/feeder/ui/pages/AboutPage.kt:137`
  - `app/src/main/AndroidManifest.xml:108`

### mipmap/ic_launcher_foreground
- Files: `mipmap-hdpi/ic_launcher_foreground.png`, `mipmap-mdpi/ic_launcher_foreground.png`, `mipmap-xhdpi/ic_launcher_foreground.png`, `mipmap-xxhdpi/ic_launcher_foreground.png`, `mipmap-xxxhdpi/ic_launcher_foreground.png`
- Used in (2):
  - `app/src/main/res/mipmap-anydpi/ic_launcher_round.xml:4`
  - `app/src/main/res/mipmap-anydpi/ic_launcher.xml:4`

### mipmap/ic_launcher_monochrome
- Files: `mipmap-hdpi/ic_launcher_monochrome.png`, `mipmap-mdpi/ic_launcher_monochrome.png`, `mipmap-xhdpi/ic_launcher_monochrome.png`, `mipmap-xxhdpi/ic_launcher_monochrome.png`, `mipmap-xxxhdpi/ic_launcher_monochrome.png`
- Used in (2):
  - `app/src/main/res/mipmap-anydpi/ic_launcher_round.xml:5`
  - `app/src/main/res/mipmap-anydpi/ic_launcher.xml:5`

### mipmap/ic_launcher_round
- Files: `mipmap-anydpi/ic_launcher_round.xml`
- Used in (1):
  - `app/src/main/AndroidManifest.xml:110`
