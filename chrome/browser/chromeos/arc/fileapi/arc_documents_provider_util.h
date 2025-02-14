// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
//
// Utilities for ARC documents provider file system.

#ifndef CHROME_BROWSER_CHROMEOS_ARC_FILEAPI_ARC_DOCUMENTS_PROVIDER_UTIL_H_
#define CHROME_BROWSER_CHROMEOS_ARC_FILEAPI_ARC_DOCUMENTS_PROVIDER_UTIL_H_

#include <string>

#include "base/files/file_path.h"

class GURL;

namespace storage {
class FileSystemURL;
}  // namespace storage

namespace arc {

// The name of ARC documents provider file system mount point.
extern const char kDocumentsProviderMountPointName[];

// The path of ARC documents provider file system mount point.
extern const base::FilePath::CharType kDocumentsProviderMountPointPath[];

// MIME type for directories in Android.
// Defined as DocumentsContract.Document.MIME_TYPE_DIR in Android.
extern const char kAndroidDirectoryMimeType[];

// Escapes a string so it can be used as a file/directory name.
// [%/.] are escaped with percent-encoding.
// NOTE: This function is visible only for unit testing. Usually you should not
// call this function directly.
std::string EscapePathComponent(const std::string& name);

// Unescapes a string escaped by EscapePathComponent().
// NOTE: This function is visible only for unit testing. Usually you should not
// call this function directly.
std::string UnescapePathComponent(const std::string& escaped);

// Returns the path of a directory where the specified DocumentsProvider is
// mounted.
// Appropriate escaping is done to embed |authority| and |root_document_id| in
// a file path.
base::FilePath GetDocumentsProviderMountPath(
    const std::string& authority,
    const std::string& root_document_id);

// Parses a FileSystemURL pointing to ARC documents provider file system.
// Appropriate unescaping is done to extract |authority| and |root_document_id|
// from |url|.
// On success, true is returned. All arguments must not be nullptr.
bool ParseDocumentsProviderUrl(const storage::FileSystemURL& url,
                               std::string* authority,
                               std::string* root_document_id,
                               base::FilePath* path);

// C++ implementation of DocumentsContract.buildDocumentUri() in Android.
GURL BuildDocumentUrl(const std::string& authority,
                      const std::string& document_id);

}  // namespace arc

#endif  // CHROME_BROWSER_CHROMEOS_ARC_FILEAPI_ARC_DOCUMENTS_PROVIDER_UTIL_H_
