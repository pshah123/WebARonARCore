// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "storage/browser/fileapi/file_system_context.h"

#include <stddef.h>

#include "base/files/scoped_temp_dir.h"
#include "base/macros.h"
#include "base/strings/stringprintf.h"
#include "base/threading/thread_task_runner_handle.h"
#include "build/build_config.h"
#include "content/browser/quota/mock_quota_manager.h"
#include "content/public/test/mock_special_storage_policy.h"
#include "content/public/test/test_file_system_options.h"
#include "storage/browser/fileapi/external_mount_points.h"
#include "storage/browser/fileapi/file_system_backend.h"
#include "storage/browser/fileapi/isolated_context.h"
#include "testing/gtest/include/gtest/gtest.h"

#define FPL(x) FILE_PATH_LITERAL(x)

#if defined(FILE_PATH_USES_DRIVE_LETTERS)
#define DRIVE FPL("C:")
#else
#define DRIVE
#endif

using storage::ExternalMountPoints;
using storage::FileSystemBackend;
using storage::FileSystemContext;
using storage::FileSystemMountOption;
using storage::FileSystemURL;
using storage::IsolatedContext;

namespace content {

namespace {

const char kTestOrigin[] = "http://chromium.org/";

GURL CreateRawFileSystemURL(const std::string& type_str,
                            const std::string& fs_id) {
  std::string url_str = base::StringPrintf(
      "filesystem:http://chromium.org/%s/%s/root/file",
      type_str.c_str(),
      fs_id.c_str());
  return GURL(url_str);
}

class FileSystemContextTest : public testing::Test {
 public:
  FileSystemContextTest() {}

  void SetUp() override {
    ASSERT_TRUE(data_dir_.CreateUniqueTempDir());

    storage_policy_ = new MockSpecialStoragePolicy();

    mock_quota_manager_ = new MockQuotaManager(
        false /* is_incognito */, data_dir_.GetPath(),
        base::ThreadTaskRunnerHandle::Get().get(),
        base::ThreadTaskRunnerHandle::Get().get(), storage_policy_.get());
  }

 protected:
  FileSystemContext* CreateFileSystemContextForTest(
      storage::ExternalMountPoints* external_mount_points) {
    return new FileSystemContext(
        base::ThreadTaskRunnerHandle::Get().get(),
        base::ThreadTaskRunnerHandle::Get().get(), external_mount_points,
        storage_policy_.get(), mock_quota_manager_->proxy(),
        std::vector<std::unique_ptr<FileSystemBackend>>(),
        std::vector<storage::URLRequestAutoMountHandler>(), data_dir_.GetPath(),
        CreateAllowFileAccessOptions());
  }

  // Verifies a *valid* filesystem url has expected values.
  void ExpectFileSystemURLMatches(const FileSystemURL& url,
                                  const GURL& expect_origin,
                                  storage::FileSystemType expect_mount_type,
                                  storage::FileSystemType expect_type,
                                  const base::FilePath& expect_path,
                                  const base::FilePath& expect_virtual_path,
                                  const std::string& expect_filesystem_id) {
    EXPECT_TRUE(url.is_valid());

    EXPECT_EQ(expect_origin, url.origin());
    EXPECT_EQ(expect_mount_type, url.mount_type());
    EXPECT_EQ(expect_type, url.type());
    EXPECT_EQ(expect_path, url.path());
    EXPECT_EQ(expect_virtual_path, url.virtual_path());
    EXPECT_EQ(expect_filesystem_id, url.filesystem_id());
  }

 private:
  base::ScopedTempDir data_dir_;
  base::MessageLoop message_loop_;
  scoped_refptr<storage::SpecialStoragePolicy> storage_policy_;
  scoped_refptr<MockQuotaManager> mock_quota_manager_;
};

// It is not valid to pass NULL ExternalMountPoints to FileSystemContext on
// ChromeOS.
#if !defined(OS_CHROMEOS)
TEST_F(FileSystemContextTest, NullExternalMountPoints) {
  scoped_refptr<FileSystemContext> file_system_context(
      CreateFileSystemContextForTest(NULL));

  // Cracking system external mount and isolated mount points should work.
  std::string isolated_name = "root";
  std::string isolated_id =
      IsolatedContext::GetInstance()->RegisterFileSystemForPath(
          storage::kFileSystemTypeNativeLocal,
          std::string(),
          base::FilePath(DRIVE FPL("/test/isolated/root")),
          &isolated_name);
  // Register system external mount point.
  ASSERT_TRUE(ExternalMountPoints::GetSystemInstance()->RegisterFileSystem(
      "system",
      storage::kFileSystemTypeNativeLocal,
      FileSystemMountOption(),
      base::FilePath(DRIVE FPL("/test/sys/"))));

  FileSystemURL cracked_isolated = file_system_context->CrackURL(
      CreateRawFileSystemURL("isolated", isolated_id));

  ExpectFileSystemURLMatches(
      cracked_isolated,
      GURL(kTestOrigin),
      storage::kFileSystemTypeIsolated,
      storage::kFileSystemTypeNativeLocal,
      base::FilePath(DRIVE FPL("/test/isolated/root/file"))
          .NormalizePathSeparators(),
      base::FilePath::FromUTF8Unsafe(isolated_id)
          .Append(FPL("root/file"))
          .NormalizePathSeparators(),
      isolated_id);

  FileSystemURL cracked_external = file_system_context->CrackURL(
      CreateRawFileSystemURL("external", "system"));

  ExpectFileSystemURLMatches(
      cracked_external,
      GURL(kTestOrigin),
      storage::kFileSystemTypeExternal,
      storage::kFileSystemTypeNativeLocal,
      base::FilePath(DRIVE FPL("/test/sys/root/file"))
          .NormalizePathSeparators(),
      base::FilePath(FPL("system/root/file")).NormalizePathSeparators(),
      "system");

  IsolatedContext::GetInstance()->RevokeFileSystem(isolated_id);
  ExternalMountPoints::GetSystemInstance()->RevokeFileSystem("system");
}
#endif  // !defiend(OS_CHROMEOS)

TEST_F(FileSystemContextTest, FileSystemContextKeepsMountPointsAlive) {
  scoped_refptr<ExternalMountPoints> mount_points =
      ExternalMountPoints::CreateRefCounted();

  // Register system external mount point.
  ASSERT_TRUE(mount_points->RegisterFileSystem(
      "system",
      storage::kFileSystemTypeNativeLocal,
      FileSystemMountOption(),
      base::FilePath(DRIVE FPL("/test/sys/"))));

  scoped_refptr<FileSystemContext> file_system_context(
      CreateFileSystemContextForTest(mount_points.get()));

  // Release a MountPoints reference created in the test.
  mount_points = NULL;

  // FileSystemContext should keep a reference to the |mount_points|, so it
  // should be able to resolve the URL.
  FileSystemURL cracked_external = file_system_context->CrackURL(
      CreateRawFileSystemURL("external", "system"));

  ExpectFileSystemURLMatches(
      cracked_external,
      GURL(kTestOrigin),
      storage::kFileSystemTypeExternal,
      storage::kFileSystemTypeNativeLocal,
      base::FilePath(DRIVE FPL("/test/sys/root/file"))
          .NormalizePathSeparators(),
      base::FilePath(FPL("system/root/file")).NormalizePathSeparators(),
      "system");

  // No need to revoke the registered filesystem since |mount_points| lifetime
  // is bound to this test.
}

TEST_F(FileSystemContextTest, CrackFileSystemURL) {
  scoped_refptr<ExternalMountPoints> external_mount_points(
      ExternalMountPoints::CreateRefCounted());
  scoped_refptr<FileSystemContext> file_system_context(
      CreateFileSystemContextForTest(external_mount_points.get()));

  // Register an isolated mount point.
  std::string isolated_file_system_name = "root";
  const std::string kIsolatedFileSystemID =
      IsolatedContext::GetInstance()->RegisterFileSystemForPath(
          storage::kFileSystemTypeNativeLocal,
          std::string(),
          base::FilePath(DRIVE FPL("/test/isolated/root")),
          &isolated_file_system_name);
  // Register system external mount point.
  ASSERT_TRUE(ExternalMountPoints::GetSystemInstance()->RegisterFileSystem(
      "system",
      storage::kFileSystemTypeDrive,
      FileSystemMountOption(),
      base::FilePath(DRIVE FPL("/test/sys/"))));
  ASSERT_TRUE(ExternalMountPoints::GetSystemInstance()->RegisterFileSystem(
      "ext",
      storage::kFileSystemTypeNativeLocal,
      FileSystemMountOption(),
      base::FilePath(DRIVE FPL("/test/ext"))));
  // Register a system external mount point with the same name/id as the
  // registered isolated mount point.
  ASSERT_TRUE(ExternalMountPoints::GetSystemInstance()->RegisterFileSystem(
      kIsolatedFileSystemID,
      storage::kFileSystemTypeRestrictedNativeLocal,
      FileSystemMountOption(),
      base::FilePath(DRIVE FPL("/test/system/isolated"))));
  // Add a mount points with the same name as a system mount point to
  // FileSystemContext's external mount points.
  ASSERT_TRUE(external_mount_points->RegisterFileSystem(
      "ext",
      storage::kFileSystemTypeNativeLocal,
      FileSystemMountOption(),
      base::FilePath(DRIVE FPL("/test/local/ext/"))));

  const GURL kTestOrigin = GURL("http://chromium.org/");
  const base::FilePath kVirtualPathNoRoot = base::FilePath(FPL("root/file"));

  struct TestCase {
    // Test case values.
    std::string root;
    std::string type_str;

    // Expected test results.
    bool expect_is_valid;
    storage::FileSystemType expect_mount_type;
    storage::FileSystemType expect_type;
    const base::FilePath::CharType* expect_path;
    std::string expect_filesystem_id;
  };

  const TestCase kTestCases[] = {
      // Following should not be handled by the url crackers:
      {
       "pers_mount", "persistent", true /* is_valid */,
       storage::kFileSystemTypePersistent, storage::kFileSystemTypePersistent,
       FPL("pers_mount/root/file"), std::string() /* filesystem id */
      },
      {
       "temp_mount", "temporary", true /* is_valid */,
       storage::kFileSystemTypeTemporary, storage::kFileSystemTypeTemporary,
       FPL("temp_mount/root/file"), std::string() /* filesystem id */
      },
      // Should be cracked by isolated mount points:
      {kIsolatedFileSystemID, "isolated", true /* is_valid */,
       storage::kFileSystemTypeIsolated, storage::kFileSystemTypeNativeLocal,
       DRIVE FPL("/test/isolated/root/file"), kIsolatedFileSystemID},
      // Should be cracked by system mount points:
      {"system", "external", true /* is_valid */,
       storage::kFileSystemTypeExternal, storage::kFileSystemTypeDrive,
       DRIVE FPL("/test/sys/root/file"), "system"},
      {kIsolatedFileSystemID, "external", true /* is_valid */,
       storage::kFileSystemTypeExternal,
       storage::kFileSystemTypeRestrictedNativeLocal,
       DRIVE FPL("/test/system/isolated/root/file"), kIsolatedFileSystemID},
      // Should be cracked by FileSystemContext's ExternalMountPoints.
      {"ext", "external", true /* is_valid */, storage::kFileSystemTypeExternal,
       storage::kFileSystemTypeNativeLocal,
       DRIVE FPL("/test/local/ext/root/file"), "ext"},
      // Test for invalid filesystem url (made invalid by adding invalid
      // filesystem type).
      {"sytem", "external", false /* is_valid */,
       // The rest of values will be ignored.
       storage::kFileSystemTypeUnknown, storage::kFileSystemTypeUnknown,
       FPL(""), std::string()},
      // Test for URL with non-existing filesystem id.
      {"invalid", "external", false /* is_valid */,
       // The rest of values will be ignored.
       storage::kFileSystemTypeUnknown, storage::kFileSystemTypeUnknown,
       FPL(""), std::string()},
  };

  for (size_t i = 0; i < arraysize(kTestCases); ++i) {
    const base::FilePath virtual_path =
        base::FilePath::FromUTF8Unsafe(
            kTestCases[i].root).Append(kVirtualPathNoRoot);

    GURL raw_url =
        CreateRawFileSystemURL(kTestCases[i].type_str, kTestCases[i].root);
    FileSystemURL cracked_url = file_system_context->CrackURL(raw_url);

    SCOPED_TRACE(testing::Message() << "Test case " << i << ": "
                                    << "Cracking URL: " << raw_url);

    EXPECT_EQ(kTestCases[i].expect_is_valid, cracked_url.is_valid());
    if (!kTestCases[i].expect_is_valid)
      continue;

    ExpectFileSystemURLMatches(
        cracked_url,
        GURL(kTestOrigin),
        kTestCases[i].expect_mount_type,
        kTestCases[i].expect_type,
        base::FilePath(kTestCases[i].expect_path).NormalizePathSeparators(),
        virtual_path.NormalizePathSeparators(),
        kTestCases[i].expect_filesystem_id);
  }

  IsolatedContext::GetInstance()->RevokeFileSystemByPath(
      base::FilePath(DRIVE FPL("/test/isolated/root")));
  ExternalMountPoints::GetSystemInstance()->RevokeFileSystem("system");
  ExternalMountPoints::GetSystemInstance()->RevokeFileSystem("ext");
  ExternalMountPoints::GetSystemInstance()->RevokeFileSystem(
      kIsolatedFileSystemID);
}

TEST_F(FileSystemContextTest, CanServeURLRequest) {
  scoped_refptr<ExternalMountPoints> external_mount_points(
      ExternalMountPoints::CreateRefCounted());
  scoped_refptr<FileSystemContext> context(
      CreateFileSystemContextForTest(external_mount_points.get()));

  // A request for a sandbox mount point should be served.
  FileSystemURL cracked_url =
      context->CrackURL(CreateRawFileSystemURL("persistent", "pers_mount"));
  EXPECT_EQ(storage::kFileSystemTypePersistent, cracked_url.mount_type());
  EXPECT_TRUE(context->CanServeURLRequest(cracked_url));

  // A request for an isolated mount point should NOT be served.
  std::string isolated_fs_name = "root";
  std::string isolated_fs_id =
      IsolatedContext::GetInstance()->RegisterFileSystemForPath(
          storage::kFileSystemTypeNativeLocal,
          std::string(),
          base::FilePath(DRIVE FPL("/test/isolated/root")),
          &isolated_fs_name);
  cracked_url = context->CrackURL(
      CreateRawFileSystemURL("isolated", isolated_fs_id));
  EXPECT_EQ(storage::kFileSystemTypeIsolated, cracked_url.mount_type());
  EXPECT_FALSE(context->CanServeURLRequest(cracked_url));

  // A request for an external mount point should be served.
  const std::string kExternalMountName = "ext_mount";
  ASSERT_TRUE(ExternalMountPoints::GetSystemInstance()->RegisterFileSystem(
      kExternalMountName,
      storage::kFileSystemTypeDrive,
      FileSystemMountOption(),
      base::FilePath()));
  cracked_url = context->CrackURL(
      CreateRawFileSystemURL("external", kExternalMountName));
  EXPECT_EQ(storage::kFileSystemTypeExternal, cracked_url.mount_type());
  EXPECT_TRUE(context->CanServeURLRequest(cracked_url));

  ExternalMountPoints::GetSystemInstance()->RevokeFileSystem(
      kExternalMountName);
  IsolatedContext::GetInstance()->RevokeFileSystem(isolated_fs_id);
}

// Ensures that a backend exists for each common isolated file system type.
// See http://crbug.com/447027
TEST_F(FileSystemContextTest, IsolatedFileSystemsTypesHandled) {
  // This does not provide any "additional" file system handlers. In particular,
  // on Chrome OS it does not provide chromeos::FileSystemBackend.
  scoped_refptr<FileSystemContext> file_system_context(
      CreateFileSystemContextForTest(nullptr));

  // Isolated file system types are handled.
  EXPECT_TRUE(file_system_context->GetFileSystemBackend(
      storage::kFileSystemTypeIsolated));
  EXPECT_TRUE(file_system_context->GetFileSystemBackend(
      storage::kFileSystemTypeDragged));
  EXPECT_TRUE(file_system_context->GetFileSystemBackend(
      storage::kFileSystemTypeForTransientFile));
  EXPECT_TRUE(file_system_context->GetFileSystemBackend(
      storage::kFileSystemTypeNativeLocal));
  EXPECT_TRUE(file_system_context->GetFileSystemBackend(
      storage::kFileSystemTypeNativeForPlatformApp));
}

}  // namespace

}  // namespace content
