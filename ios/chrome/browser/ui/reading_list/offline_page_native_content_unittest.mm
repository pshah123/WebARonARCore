// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#import "ios/chrome/browser/ui/reading_list/offline_page_native_content.h"

#include <memory>

#import "base/mac/scoped_nsobject.h"
#include "base/strings/sys_string_conversions.h"
#include "ios/chrome/browser/browser_state/test_chrome_browser_state.h"
#include "ios/chrome/browser/reading_list/offline_url_utils.h"
#import "ios/chrome/browser/ui/static_content/static_html_view_controller.h"
#import "ios/chrome/browser/ui/url_loader.h"
#import "ios/web/public/test/web_test_with_web_state.h"
#include "testing/gtest/include/gtest/gtest.h"
#include "testing/gtest_mac.h"
#import "third_party/ocmock/OCMock/OCMock.h"
#include "third_party/ocmock/gtest_support.h"

class OfflinePageNativeContentTest : public web::WebTestWithWebState {
 protected:
  void SetUp() override {
    web::WebTestWithWebState::SetUp();
    TestChromeBrowserState::Builder test_cbs_builder;
    chrome_browser_state_ = test_cbs_builder.Build();
  }
  std::unique_ptr<TestChromeBrowserState> chrome_browser_state_;
};

// Checks the OfflinePageNativeContent is correctly initialized.
TEST_F(OfflinePageNativeContentTest, BasicOfflinePageTest) {
  GURL foo_url("http://foo.bar");
  GURL url = reading_list::DistilledURLForPath(
      base::FilePath("offline_id/page.html"), foo_url);
  id<UrlLoader> loader = [OCMockObject mockForProtocol:@protocol(UrlLoader)];
  base::scoped_nsobject<OfflinePageNativeContent> content(
      [[OfflinePageNativeContent alloc]
          initWithLoader:loader
            browserState:chrome_browser_state_.get()
                webState:web_state()
                     URL:url]);
  ASSERT_EQ(url, [content url]);
  ASSERT_EQ(foo_url, [content virtualURL]);
  ASSERT_OCMOCK_VERIFY((OCMockObject*)loader);
}

TEST_F(OfflinePageNativeContentTest, BasicOfflinePageTestWithoutVirtualURL) {
  GURL url = reading_list::DistilledURLForPath(
      base::FilePath("offline_id/page.html"), GURL());
  id<UrlLoader> loader = [OCMockObject mockForProtocol:@protocol(UrlLoader)];
  base::scoped_nsobject<OfflinePageNativeContent> content(
      [[OfflinePageNativeContent alloc]
          initWithLoader:loader
            browserState:chrome_browser_state_.get()
                webState:web_state()
                     URL:url]);
  ASSERT_EQ(url, [content url]);
  ASSERT_EQ(url, [content virtualURL]);
  ASSERT_OCMOCK_VERIFY((OCMockObject*)loader);
}
