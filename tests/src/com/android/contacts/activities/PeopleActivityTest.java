/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.contacts.activities;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.Loader;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Directory;
import android.provider.ContactsContract.Groups;
import android.provider.ContactsContract.ProviderStatus;
import android.provider.Settings;
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.SmallTest;
import android.widget.TextView;

import com.android.contacts.ContactsApplication;
import com.android.contacts.R;
import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.testing.InjectedServices;
import com.android.contacts.common.test.mocks.ContactsMockContext;
import com.android.contacts.common.test.mocks.MockContentProvider;
import com.android.contacts.common.test.mocks.MockContentProvider.Query;
import com.android.contacts.interactions.TestLoaderManager;
import com.android.contacts.list.ContactBrowseListFragment;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.model.account.BaseAccountType;
import com.android.contacts.common.preference.ContactsPreferences;
import com.android.contacts.common.test.mocks.MockAccountTypeManager;
import com.android.contacts.common.test.mocks.MockContactPhotoManager;
import com.android.contacts.common.test.mocks.MockSharedPreferences;
import com.android.contacts.util.PhoneCapabilityTester;

/**
 * This test is so outdated that it's disabled temporarily.  TODO Update the test and re-enable it.
 *
 * Tests for {@link PeopleActivity}.
 *
 * Running all tests:
 *
 *   runtest contacts
 * or
 *   adb shell am instrument \
 *     -w com.android.contacts.tests/android.test.InstrumentationTestRunner
 *
 */
@SmallTest
public class PeopleActivityTest
        extends ActivityInstrumentationTestCase2<PeopleActivity>
{
    static {
        // AsyncTask class needs to be initialized on the main thread.
        AsyncTask.init();
    }

    private static final String TEST_ACCOUNT = "testAccount";
    private static final String TEST_ACCOUNT_TYPE = "testAccountType";

    private ContactsMockContext mContext;
    private MockContentProvider mContactsProvider;
    private MockContentProvider mSettingsProvider;

    public PeopleActivityTest() {
        super(PeopleActivity.class);
    }

    @Override
    public void setUp() {
        mContext = new ContactsMockContext(getInstrumentation().getTargetContext());
        mContactsProvider = mContext.getContactsProvider();
        // The ContactsApplication performs this getType query to warm up the provider - see
        // ContactsApplication#DelayedInitialization.doInBackground
        mContactsProvider.expectTypeQuery(ContentUris.withAppendedId(Contacts.CONTENT_URI, 1),
                Contacts.CONTENT_ITEM_TYPE);
        mSettingsProvider = mContext.getSettingsProvider();
        InjectedServices services = new InjectedServices();
        services.setContentResolver(mContext.getContentResolver());
        services.setSharedPreferences(new MockSharedPreferences());
        services.setSystemService(ContactPhotoManager.CONTACT_PHOTO_SERVICE,
                new MockContactPhotoManager());
        AccountType accountType = new BaseAccountType() {
            @Override
            public boolean areContactsWritable() {
                return false;
            }
        };
        accountType.accountType = TEST_ACCOUNT_TYPE;

        AccountWithDataSet account = new AccountWithDataSet(TEST_ACCOUNT, TEST_ACCOUNT_TYPE, null);
        ContactsApplication.injectServices(services);

        final MockAccountTypeManager mockManager = new MockAccountTypeManager(
                        new AccountType[] { accountType }, new AccountWithDataSet[] { account });
        AccountTypeManager.setInstanceForTest(mockManager);
    }

    @Override
    protected void tearDown() throws Exception {
        ContactsApplication.injectServices(null);
        super.tearDown();
    }

    private void expectProviderStatusQueryAndReturnNormal() {
        mContactsProvider
                .expectQuery(ProviderStatus.CONTENT_URI)
                .withProjection(ProviderStatus.STATUS, ProviderStatus.DATA1)
                .returnRow(ProviderStatus.STATUS_NORMAL, null)
                .anyNumberOfTimes();
    }

    private void expectGroupsQueryAndReturnEmpty() {
        mContactsProvider
                .expectQuery(Groups.CONTENT_URI)
                .withAnyProjection()
                .withAnySelection()
                .returnEmptyCursor()
                .anyNumberOfTimes();
    }

    private void expectContactListQuery(int count) {
        Uri uri = Contacts.CONTENT_URI.buildUpon()
                .appendQueryParameter(Contacts.EXTRA_ADDRESS_BOOK_INDEX, "true")
                .appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY,
                        String.valueOf(Directory.DEFAULT))
                .build();

        Query query = mContactsProvider
                .expectQuery(uri)
                .withAnyProjection()
                .withSortOrder(Contacts.SORT_KEY_PRIMARY);
        for (int i = 1; i <= count; i++) {
            ContentValues values = new ContentValues();
            values.put(Contacts._ID, i);
            values.put(Contacts.DISPLAY_NAME, "Contact " + i);
            values.put(Contacts.SORT_KEY_PRIMARY, "contact " + i);
            values.put(Contacts.LOOKUP_KEY, "lu" + i);
            query.returnRow(values);
        }
    }

    private void expectContactLookupQuery(
            String lookupKey, long id, String returnLookupKey, long returnId) {
        Uri uri = Contacts.getLookupUri(id, lookupKey);
        mContactsProvider.expectTypeQuery(uri, Contacts.CONTENT_ITEM_TYPE);
        mContactsProvider
                .expectQuery(uri)
                .withProjection(Contacts._ID, Contacts.LOOKUP_KEY)
                .returnRow(returnId, returnLookupKey);
    }

    private void expectContactEntityQuery(String lookupKey, int contactId) {
        Uri uri = Uri.withAppendedPath(
                Contacts.getLookupUri(contactId, lookupKey), Contacts.Entity.CONTENT_DIRECTORY);
        ContentValues row1 = new ContentValues();
        row1.put(Contacts.Entity.DATA_ID, 1);
        row1.put(Contacts.Entity.LOOKUP_KEY, lookupKey);
        row1.put(Contacts.Entity.CONTACT_ID, contactId);
        row1.put(Contacts.Entity.DISPLAY_NAME, "Contact " + contactId);
        row1.put(Contacts.Entity.ACCOUNT_NAME, TEST_ACCOUNT);
        row1.put(Contacts.Entity.ACCOUNT_TYPE, TEST_ACCOUNT_TYPE);
        mContactsProvider
                .expectQuery(uri)
                .withAnyProjection()
                .withAnySortOrder()
                .returnRow(row1)
                .anyNumberOfTimes();
    }
}
