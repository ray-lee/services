/**
 * This document is a part of the source code and related artifacts
 * for CollectionSpace, an open source collections management system
 * for museums and related institutions:
 *
 * http://www.collectionspace.org
 * http://wiki.collectionspace.org
 *
 * Copyright (c)) 2009 Regents of the University of California
 *
 * Licensed under the Educational Community License (ECL), Version 2.0.
 * You may not use this file except in compliance with this License.
 *
 * You may obtain a copy of the ECL 2.0 License at
 * https://source.collectionspace.org/collection-space/LICENSE.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.collectionspace.services.client.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.collectionspace.services.OrganizationJAXBSchema;
import org.collectionspace.services.client.OrgAuthorityClient;
import org.collectionspace.services.client.OrgAuthorityClientUtils;
import org.collectionspace.services.organization.OrgauthoritiesCommon;
import org.collectionspace.services.organization.OrgauthoritiesCommonList;
import org.collectionspace.services.organization.OrganizationsCommon;
import org.collectionspace.services.organization.OrganizationsCommonList;

import org.jboss.resteasy.client.ClientResponse;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;
import org.jboss.resteasy.plugins.providers.multipart.MultipartOutput;
import org.jboss.resteasy.plugins.providers.multipart.OutputPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

/**
 * OrgAuthorityServiceTest, carries out tests against a
 * deployed and running OrgAuthority Service.
 *
 * $LastChangedRevision: 753 $
 * $LastChangedDate: 2009-09-23 11:03:36 -0700 (Wed, 23 Sep 2009) $
 */
public class OrgAuthorityServiceTest extends AbstractServiceTestImpl {

    private final Logger logger =
        LoggerFactory.getLogger(OrgAuthorityServiceTest.class);

    // Instance variables specific to this test.
    private OrgAuthorityClient client = new OrgAuthorityClient();
    final String SERVICE_PATH_COMPONENT = "orgauthorities";
    final String ITEM_SERVICE_PATH_COMPONENT = "items";
    private final String TEST_ORG_SHORTNAME = "Test Org";
    private final String TEST_ORG_FOUNDING_PLACE = "Anytown, USA";
    private String knownResourceId = null;
    private String lastOrgAuthId = null;
    private String knownResourceRefName = null;
    private String knownItemResourceId = null;
    private int nItemsToCreateInList = 3;
    private List<String> allResourceIdsCreated = new ArrayList<String>();
    private Map<String, String> allResourceItemIdsCreated =
        new HashMap<String, String>();
    
    // ---------------------------------------------------------------
    // CRUD tests : CREATE tests
    // ---------------------------------------------------------------
    // Success outcomes
    @Override
    @Test(dataProvider="testName", dataProviderClass=AbstractServiceTestImpl.class)
    public void create(String testName) throws Exception {

        // Perform setup, such as initializing the type of service request
        // (e.g. CREATE, DELETE), its valid and expected status codes, and
        // its associated HTTP method name (e.g. POST, DELETE).
        setupCreate(testName);

        // Submit the request to the service and store the response.
        String identifier = createIdentifier();
        String displayName = "displayName-" + identifier;
    	String refName = OrgAuthorityClientUtils.createOrgAuthRefName(displayName, true);
    	MultipartOutput multipart = 
    		OrgAuthorityClientUtils.createOrgAuthorityInstance(
				displayName, refName, 
				client.getCommonPartName());
        ClientResponse<Response> res = client.create(multipart);
        int statusCode = res.getStatus();

        // Check the status code of the response: does it match
        // the expected response(s)?
        //
        // Specifically:
        // Does it fall within the set of valid status codes?
        // Does it exactly match the expected status code?
        if(logger.isDebugEnabled()){
            logger.debug(testName + ": status = " + statusCode);
        }
        Assert.assertTrue(REQUEST_TYPE.isValidStatusCode(statusCode),
                invalidStatusCodeMessage(REQUEST_TYPE, statusCode));
        Assert.assertEquals(statusCode, EXPECTED_STATUS_CODE);

        // Store the refname from the first resource created
        // for additional tests below.
        knownResourceRefName = refName;

        lastOrgAuthId = extractId(res);
        // Store the ID returned from the first resource created
        // for additional tests below.
        if (knownResourceId == null){
            knownResourceId = lastOrgAuthId;
            if (logger.isDebugEnabled()) {
                logger.debug(testName + ": knownResourceId=" + knownResourceId);
            }
        }
        // Store the IDs from every resource created by tests,
        // so they can be deleted after tests have been run.
        allResourceIdsCreated.add(extractId(res));

    }

    @Test(dataProvider="testName", dataProviderClass=AbstractServiceTestImpl.class,
        dependsOnMethods = {"create"})
    public void createItem(String testName) {
        setupCreate(testName);

        knownItemResourceId = createItemInAuthority(lastOrgAuthId, knownResourceRefName);
        if(logger.isDebugEnabled()){
            logger.debug(testName + ": knownItemResourceId=" + knownItemResourceId);
        }
    }

    private String createItemInAuthority(String vcsid, String orgAuthorityRefName) {

        final String testName = "createItemInAuthority";
        if(logger.isDebugEnabled()){
            logger.debug(testName + ":...");
        }

        // Submit the request to the service and store the response.
        String identifier = createIdentifier();
        String refName = OrgAuthorityClientUtils.createOrganizationRefName(knownResourceRefName, identifier, true);
        Map<String, String> testOrgMap = new HashMap<String,String>();
        testOrgMap.put(OrganizationJAXBSchema.SHORT_NAME, TEST_ORG_SHORTNAME);
        testOrgMap.put(OrganizationJAXBSchema.LONG_NAME, "The real official test organization");
        testOrgMap.put(OrganizationJAXBSchema.CONTACT_NAME, "joe@test.org");
        testOrgMap.put(OrganizationJAXBSchema.FOUNDING_DATE, "May 26, 1907");
        testOrgMap.put(OrganizationJAXBSchema.FOUNDING_PLACE, TEST_ORG_FOUNDING_PLACE);
        testOrgMap.put(OrganizationJAXBSchema.FUNCTION, "For testing");
        String newID = OrgAuthorityClientUtils.createItemInAuthority(
        		vcsid, orgAuthorityRefName, testOrgMap, client);
        // Store the ID returned from the first item resource created
        // for additional tests below.
        if (knownItemResourceId == null){
            knownItemResourceId = newID;
            if (logger.isDebugEnabled()) {
                logger.debug(testName + ": knownItemResourceId=" + knownItemResourceId);
            }
        }

        // Store the IDs from any item resources created
        // by tests, along with the IDs of their parents, so these items
        // can be deleted after all tests have been run.
        allResourceItemIdsCreated.put(newID, vcsid);

        return newID;
    }

    @Override
    @Test(dataProvider="testName", dataProviderClass=AbstractServiceTestImpl.class,
            dependsOnMethods = {"create", "createItem"})
    public void createList(String testName) throws Exception {
        for (int i = 0; i < 3; i++) {
            create(testName);
            knownResourceId = lastOrgAuthId;
            if (logger.isDebugEnabled()) {
                logger.debug(testName + ": Resetting knownResourceId to" + knownResourceId);
            }
            // Add nItemsToCreateInList items to each orgauthority
            for (int j = 0; j < nItemsToCreateInList; j++) {
                createItem(testName);
            }
        }
    }

    // Failure outcomes
    // Placeholders until the three tests below can be uncommented.
    // See Issue CSPACE-401.
    @Override
    public void createWithEmptyEntityBody(String testName) throws Exception {
    }

    @Override
    public void createWithMalformedXml(String testName) throws Exception {
    }

    @Override
    public void createWithWrongXmlSchema(String testName) throws Exception {
    }

    /*
    @Override
    @Test(dataProvider="testName", dataProviderClass=AbstractServiceTest.class,
        dependsOnMethods = {"create", "testSubmitRequest"})
    public void createWithEmptyEntityBody(String testName) throws Exception {

    // Perform setup.
    setupCreateWithEmptyEntityBody(testName);

    // Submit the request to the service and store the response.
    String method = REQUEST_TYPE.httpMethodName();
    String url = getServiceRootURL();
    String mediaType = MediaType.APPLICATION_XML;
    final String entity = "";
    int statusCode = submitRequest(method, url, mediaType, entity);

    // Check the status code of the response: does it match
    // the expected response(s)?
    if(logger.isDebugEnabled()) {
        logger.debug(testName + ": url=" + url +
            " status=" + statusCode);
     }
    Assert.assertTrue(REQUEST_TYPE.isValidStatusCode(statusCode),
    invalidStatusCodeMessage(REQUEST_TYPE, statusCode));
    Assert.assertEquals(statusCode, EXPECTED_STATUS_CODE);
    }

    @Override
    @Test(dataProvider="testName", dataProviderClass=AbstractServiceTest.class,
        dependsOnMethods = {"create", "testSubmitRequest"})
    public void createWithMalformedXml(String testName) throws Exception {

    // Perform setup.
    setupCreateWithMalformedXml(testName);

    // Submit the request to the service and store the response.
    String method = REQUEST_TYPE.httpMethodName();
    String url = getServiceRootURL();
    String mediaType = MediaType.APPLICATION_XML;
    final String entity = MALFORMED_XML_DATA; // Constant from base class.
    int statusCode = submitRequest(method, url, mediaType, entity);

    // Check the status code of the response: does it match
    // the expected response(s)?
    if(logger.isDebugEnabled()){
        logger.debug(testName + ": url=" + url +
            " status=" + statusCode);
     }
    Assert.assertTrue(REQUEST_TYPE.isValidStatusCode(statusCode),
    invalidStatusCodeMessage(REQUEST_TYPE, statusCode));
    Assert.assertEquals(statusCode, EXPECTED_STATUS_CODE);
    }

    @Override
    @Test(dataProvider="testName", dataProviderClass=AbstractServiceTest.class,
        dependsOnMethods = {"create", "testSubmitRequest"})
    public void createWithWrongXmlSchema(String testName) throws Exception {

    // Perform setup.
    setupCreateWithWrongXmlSchema(testName);

    // Submit the request to the service and store the response.
    String method = REQUEST_TYPE.httpMethodName();
    String url = getServiceRootURL();
    String mediaType = MediaType.APPLICATION_XML;
    final String entity = WRONG_XML_SCHEMA_DATA;
    int statusCode = submitRequest(method, url, mediaType, entity);

    // Check the status code of the response: does it match
    // the expected response(s)?
    if(logger.isDebugEnabled()){
        logger.debug(testName + ": url=" + url +
            " status=" + statusCode);
     }
    Assert.assertTrue(REQUEST_TYPE.isValidStatusCode(statusCode),
    invalidStatusCodeMessage(REQUEST_TYPE, statusCode));
    Assert.assertEquals(statusCode, EXPECTED_STATUS_CODE);
    }
     */

    // ---------------------------------------------------------------
    // CRUD tests : READ tests
    // ---------------------------------------------------------------
    // Success outcomes
    @Override
    @Test(dataProvider="testName", dataProviderClass=AbstractServiceTestImpl.class,
        dependsOnMethods = {"create"})
    public void read(String testName) throws Exception {

        // Perform setup.
        setupRead();

        // Submit the request to the service and store the response.
        ClientResponse<MultipartInput> res = client.read(knownResourceId);
        int statusCode = res.getStatus();

        // Check the status code of the response: does it match
        // the expected response(s)?
        if(logger.isDebugEnabled()){
            logger.debug(testName + ": status = " + statusCode);
        }
        Assert.assertTrue(REQUEST_TYPE.isValidStatusCode(statusCode),
                invalidStatusCodeMessage(REQUEST_TYPE, statusCode));
        Assert.assertEquals(statusCode, EXPECTED_STATUS_CODE);
        //FIXME: remove the following try catch once Aron fixes signatures
        try {
            MultipartInput input = (MultipartInput) res.getEntity();
            OrgauthoritiesCommon orgAuthority = (OrgauthoritiesCommon) extractPart(input,
                    client.getCommonPartName(), OrgauthoritiesCommon.class);
            Assert.assertNotNull(orgAuthority);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /*
    @Test(dataProvider="testName", dataProviderClass=AbstractServiceTest.class,
            dependsOnMethods = {"read"})
        public void readByName(String testName) throws Exception {

            // Perform setup.
            setupRead();

            // Submit the request to the service and store the response.
            ClientResponse<MultipartInput> res = client.read(knownResourceId);
            int statusCode = res.getStatus();

            // Check the status code of the response: does it match
            // the expected response(s)?
            if(logger.isDebugEnabled()){
                logger.debug(testName + ": status = " + statusCode);
            }
            Assert.assertTrue(REQUEST_TYPE.isValidStatusCode(statusCode),
                    invalidStatusCodeMessage(REQUEST_TYPE, statusCode));
            Assert.assertEquals(statusCode, EXPECTED_STATUS_CODE);
            //FIXME: remove the following try catch once Aron fixes signatures
            try {
                MultipartInput input = (MultipartInput) res.getEntity();
                OrgauthoritiesCommon orgAuthority = (OrgauthoritiesCommon) extractPart(input,
                        client.getCommonPartName(), OrgauthoritiesCommon.class);
                Assert.assertNotNull(orgAuthority);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    */

    @Test(dataProvider="testName", dataProviderClass=AbstractServiceTestImpl.class,
        dependsOnMethods = {"createItem", "read"})
    public void readItem(String testName) throws Exception {

        // Perform setup.
        setupRead(testName);

        // Submit the request to the service and store the response.
        ClientResponse<MultipartInput> res = client.readItem(knownResourceId, knownItemResourceId);
        int statusCode = res.getStatus();

        // Check the status code of the response: does it match
        // the expected response(s)?
        if(logger.isDebugEnabled()){
            logger.debug(testName + ": status = " + statusCode);
        }
        Assert.assertTrue(REQUEST_TYPE.isValidStatusCode(statusCode),
                invalidStatusCodeMessage(REQUEST_TYPE, statusCode));
        Assert.assertEquals(statusCode, EXPECTED_STATUS_CODE);

        // Check whether we've received a organization.
        MultipartInput input = (MultipartInput) res.getEntity();
        OrganizationsCommon organization = (OrganizationsCommon) extractPart(input,
                client.getItemCommonPartName(), OrganizationsCommon.class);
        Assert.assertNotNull(organization);
        boolean showFull = true;
        if(showFull && logger.isDebugEnabled()){
            logger.debug(testName + ": returned payload:");
            logger.debug(objectAsXmlString(organization,
                    OrganizationsCommon.class));
        }
        Assert.assertEquals(organization.getInAuthority(), knownResourceId);
    }

    @Test(dataProvider="testName", dataProviderClass=AbstractServiceTestImpl.class,
            dependsOnMethods = {"readItem", "updateItem"})
    public void verifyItemDisplayName(String testName) throws Exception {

        // Perform setup.
        setupUpdate(testName);

        // Submit the request to the service and store the response.
        ClientResponse<MultipartInput> res = client.readItem(knownResourceId, knownItemResourceId);
        int statusCode = res.getStatus();

        // Check the status code of the response: does it match
        // the expected response(s)?
        if(logger.isDebugEnabled()){
            logger.debug(testName + ": status = " + statusCode);
        }
        Assert.assertTrue(REQUEST_TYPE.isValidStatusCode(statusCode),
                invalidStatusCodeMessage(REQUEST_TYPE, statusCode));
        Assert.assertEquals(statusCode, EXPECTED_STATUS_CODE);

        // Check whether organization has expected displayName.
        MultipartInput input = (MultipartInput) res.getEntity();
        OrganizationsCommon organization = (OrganizationsCommon) extractPart(input,
                client.getItemCommonPartName(), OrganizationsCommon.class);
        Assert.assertNotNull(organization);
        String displayName = organization.getDisplayName();
        // Make sure displayName matches computed form
        String expectedDisplayName = 
        	OrgAuthorityClientUtils.prepareDefaultDisplayName(
        			TEST_ORG_SHORTNAME, TEST_ORG_FOUNDING_PLACE);
        Assert.assertNotNull(displayName, expectedDisplayName);
        
        // Update the shortName and verify the computed name is updated.
        organization.setDisplayNameComputed(true);
        organization.setShortName("updated-" + TEST_ORG_SHORTNAME);
        expectedDisplayName = 
        	OrgAuthorityClientUtils.prepareDefaultDisplayName(
        			"updated-" + TEST_ORG_SHORTNAME, TEST_ORG_FOUNDING_PLACE);

        // Submit the updated resource to the service and store the response.
        MultipartOutput output = new MultipartOutput();
        OutputPart commonPart = output.addPart(organization, MediaType.APPLICATION_XML_TYPE);
        commonPart.getHeaders().add("label", client.getItemCommonPartName());
        res = client.updateItem(knownResourceId, knownItemResourceId, output);
        statusCode = res.getStatus();

        // Check the status code of the response: does it match the expected response(s)?
        if(logger.isDebugEnabled()){
            logger.debug("updateItem: status = " + statusCode);
        }
        Assert.assertTrue(REQUEST_TYPE.isValidStatusCode(statusCode),
                invalidStatusCodeMessage(REQUEST_TYPE, statusCode));
        Assert.assertEquals(statusCode, EXPECTED_STATUS_CODE);

        // Retrieve the updated resource and verify that its contents exist.
        input = (MultipartInput) res.getEntity();
        OrganizationsCommon updatedOrganization =
                (OrganizationsCommon) extractPart(input,
                        client.getItemCommonPartName(), OrganizationsCommon.class);
        Assert.assertNotNull(updatedOrganization);

        // Verify that the updated resource received the correct data.
        Assert.assertEquals(updatedOrganization.getShortName(),
                organization.getShortName(),
                "Updated ShortName in Organization did not match submitted data.");
        // Verify that the updated resource computes the right displayName.
        Assert.assertEquals(updatedOrganization.getDisplayName(),
        		expectedDisplayName,
                "Updated ShortName in Organization not reflected in computed DisplayName.");

        // Now Update the displayName, not computed and verify the computed name is overriden.
        organization.setDisplayNameComputed(false);
        expectedDisplayName = "TestName";
        organization.setDisplayName(expectedDisplayName);

        // Submit the updated resource to the service and store the response.
        output = new MultipartOutput();
        commonPart = output.addPart(organization, MediaType.APPLICATION_XML_TYPE);
        commonPart.getHeaders().add("label", client.getItemCommonPartName());
        res = client.updateItem(knownResourceId, knownItemResourceId, output);
        statusCode = res.getStatus();

        // Check the status code of the response: does it match the expected response(s)?
        if(logger.isDebugEnabled()){
            logger.debug("updateItem: status = " + statusCode);
        }
        Assert.assertTrue(REQUEST_TYPE.isValidStatusCode(statusCode),
                invalidStatusCodeMessage(REQUEST_TYPE, statusCode));
        Assert.assertEquals(statusCode, EXPECTED_STATUS_CODE);

        // Retrieve the updated resource and verify that its contents exist.
        input = (MultipartInput) res.getEntity();
        updatedOrganization =
                (OrganizationsCommon) extractPart(input,
                        client.getItemCommonPartName(), OrganizationsCommon.class);
        Assert.assertNotNull(updatedOrganization);

        // Verify that the updated resource received the correct data.
        Assert.assertEquals(updatedOrganization.isDisplayNameComputed(), false,
                "Updated displayNameComputed in Organization did not match submitted data.");
        // Verify that the updated resource computes the right displayName.
        Assert.assertEquals(updatedOrganization.getDisplayName(),
        		expectedDisplayName,
                "Updated DisplayName (not computed) in Organization not stored.");
    }

    @Test(dataProvider="testName", dataProviderClass=AbstractServiceTestImpl.class,
            dependsOnMethods = {"verifyItemDisplayName"})
    public void verifyIllegalItemDisplayName(String testName) throws Exception {

        // Perform setup.
    	setupUpdateWithWrongXmlSchema(testName);

        // Submit the request to the service and store the response.
        ClientResponse<MultipartInput> res = client.readItem(knownResourceId, knownItemResourceId);
        int statusCode = res.getStatus();

        // Check the status code of the response: does it match
        // the expected response(s)?
        if(logger.isDebugEnabled()){
            logger.debug(testName + ": status = " + statusCode);
        }
        Assert.assertTrue(REQUEST_TYPE.isValidStatusCode(statusCode),
                invalidStatusCodeMessage(REQUEST_TYPE, statusCode));
        Assert.assertEquals(statusCode, Response.Status.OK.getStatusCode());

        // Check whether organization has expected displayName.
        MultipartInput input = (MultipartInput) res.getEntity();
        OrganizationsCommon organization = (OrganizationsCommon) extractPart(input,
                client.getItemCommonPartName(), OrganizationsCommon.class);
        Assert.assertNotNull(organization);
        // Try to Update with computed false and no displayName
    	organization.setDisplayNameComputed(false);
        organization.setDisplayName(null);

        // Submit the updated resource to the service and store the response.
        MultipartOutput output = new MultipartOutput();
        OutputPart commonPart = output.addPart(organization, MediaType.APPLICATION_XML_TYPE);
        commonPart.getHeaders().add("label", client.getItemCommonPartName());
        res = client.updateItem(knownResourceId, knownItemResourceId, output);
        statusCode = res.getStatus();

        // Check the status code of the response: does it match the expected response(s)?
        if(logger.isDebugEnabled()){
            logger.debug("updateItem: status = " + statusCode);
        }
        Assert.assertTrue(REQUEST_TYPE.isValidStatusCode(statusCode),
                invalidStatusCodeMessage(REQUEST_TYPE, statusCode));
        Assert.assertEquals(statusCode, EXPECTED_STATUS_CODE);
    }

    // Failure outcomes
    @Override
    @Test(dataProvider="testName", dataProviderClass=AbstractServiceTestImpl.class,
        dependsOnMethods = {"read"})
    public void readNonExistent(String testName) {

        // Perform setup.
        setupReadNonExistent(testName);

        // Submit the request to the service and store the response.
        ClientResponse<MultipartInput> res = client.read(NON_EXISTENT_ID);
        int statusCode = res.getStatus();

        // Check the status code of the response: does it match
        // the expected response(s)?
        if(logger.isDebugEnabled()){
            logger.debug(testName + ": status = " + statusCode);
        }
        Assert.assertTrue(REQUEST_TYPE.isValidStatusCode(statusCode),
                invalidStatusCodeMessage(REQUEST_TYPE, statusCode));
        Assert.assertEquals(statusCode, EXPECTED_STATUS_CODE);
    }

    @Test(dataProvider="testName", dataProviderClass=AbstractServiceTestImpl.class,
        dependsOnMethods = {"readItem", "readNonExistent"})
    public void readItemNonExistent(String testName) {

        // Perform setup.
        setupReadNonExistent(testName);

        // Submit the request to the service and store the response.
        ClientResponse<MultipartInput> res = client.readItem(knownResourceId, NON_EXISTENT_ID);
        int statusCode = res.getStatus();

        // Check the status code of the response: does it match
        // the expected response(s)?
        if(logger.isDebugEnabled()){
            logger.debug(testName + ": status = " + statusCode);
        }
        Assert.assertTrue(REQUEST_TYPE.isValidStatusCode(statusCode),
                invalidStatusCodeMessage(REQUEST_TYPE, statusCode));
        Assert.assertEquals(statusCode, EXPECTED_STATUS_CODE);
    }
    // ---------------------------------------------------------------
    // CRUD tests : READ_LIST tests
    // ---------------------------------------------------------------
    // Success outcomes

    @Override
    @Test(dataProvider="testName", dataProviderClass=AbstractServiceTestImpl.class,
        dependsOnMethods = {"createList", "read"})
    public void readList(String testName) throws Exception {

        // Perform setup.
        setupReadList(testName);

        // Submit the request to the service and store the response.
        ClientResponse<OrgauthoritiesCommonList> res = client.readList();
        OrgauthoritiesCommonList list = res.getEntity();
        int statusCode = res.getStatus();

        // Check the status code of the response: does it match
        // the expected response(s)?
        if(logger.isDebugEnabled()){
            logger.debug(testName + ": status = " + statusCode);
        }
        Assert.assertTrue(REQUEST_TYPE.isValidStatusCode(statusCode),
                invalidStatusCodeMessage(REQUEST_TYPE, statusCode));
        Assert.assertEquals(statusCode, EXPECTED_STATUS_CODE);

        // Optionally output additional data about list members for debugging.
        boolean iterateThroughList = false;
        if (iterateThroughList && logger.isDebugEnabled()) {
            List<OrgauthoritiesCommonList.OrgauthorityListItem> items =
                    list.getOrgauthorityListItem();
            int i = 0;
            for (OrgauthoritiesCommonList.OrgauthorityListItem item : items) {
                String csid = item.getCsid();
                logger.debug(testName + ": list-item[" + i + "] csid=" +
                        csid);
                logger.debug(testName + ": list-item[" + i + "] displayName=" +
                        item.getDisplayName());
                logger.debug(testName + ": list-item[" + i + "] URI=" +
                        item.getUri());
                readItemList(csid);
                i++;
            }
        }
    }

    @Test(dependsOnMethods = {"createList", "readItem"})
    public void readItemList() {
        readItemList(knownResourceId);
    }

    private void readItemList(String vcsid) {

        final String testName = "readItemList";

        // Perform setup.
        setupReadList(testName);

        // Submit the request to the service and store the response.
        ClientResponse<OrganizationsCommonList> res =
                client.readItemList(vcsid);
        OrganizationsCommonList list = res.getEntity();
        int statusCode = res.getStatus();

        // Check the status code of the response: does it match
        // the expected response(s)?
        if(logger.isDebugEnabled()){
            logger.debug("  " + testName + ": status = " + statusCode);
        }
        Assert.assertTrue(REQUEST_TYPE.isValidStatusCode(statusCode),
                invalidStatusCodeMessage(REQUEST_TYPE, statusCode));
        Assert.assertEquals(statusCode, EXPECTED_STATUS_CODE);

        List<OrganizationsCommonList.OrganizationListItem> items =
            list.getOrganizationListItem();
        int nItemsReturned = items.size();
        if(logger.isDebugEnabled()){
            logger.debug("  " + testName + ": Expected "
           		+ nItemsToCreateInList+" items; got: "+nItemsReturned);
        }
        Assert.assertEquals( nItemsReturned, nItemsToCreateInList);

        int i = 0;
        for (OrganizationsCommonList.OrganizationListItem item : items) {
        	Assert.assertTrue((null != item.getRefName()), "Item refName is null!");
        	Assert.assertTrue((null != item.getDisplayName()), "Item displayName is null!");
        	// Optionally output additional data about list members for debugging.
	        boolean showDetails = true;
	        if (showDetails && logger.isDebugEnabled()) {
                logger.debug("  " + testName + ": list-item[" + i + "] csid=" +
                        item.getCsid());
                logger.debug("  " + testName + ": list-item[" + i + "] refName=" +
                        item.getRefName());
                logger.debug("  " + testName + ": list-item[" + i + "] displayName=" +
                        item.getDisplayName());
                logger.debug("  " + testName + ": list-item[" + i + "] URI=" +
                        item.getUri());
            }
            i++;
        }
    }

    // Failure outcomes
    // None at present.
    // ---------------------------------------------------------------
    // CRUD tests : UPDATE tests
    // ---------------------------------------------------------------
    // Success outcomes
    @Override
    @Test(dataProvider="testName", dataProviderClass=AbstractServiceTestImpl.class,
        dependsOnMethods = {"read"})
    public void update(String testName) throws Exception {

        // Perform setup.
        setupUpdate(testName);

        // Retrieve the contents of a resource to update.
        ClientResponse<MultipartInput> res =
                client.read(knownResourceId);
        if(logger.isDebugEnabled()){
            logger.debug(testName + ": read status = " + res.getStatus());
        }
        Assert.assertEquals(res.getStatus(), EXPECTED_STATUS_CODE);

        if(logger.isDebugEnabled()){
            logger.debug("got OrgAuthority to update with ID: " + knownResourceId);
        }
        MultipartInput input = (MultipartInput) res.getEntity();
        OrgauthoritiesCommon orgAuthority = (OrgauthoritiesCommon) extractPart(input,
                client.getCommonPartName(), OrgauthoritiesCommon.class);
        Assert.assertNotNull(orgAuthority);

        // Update the contents of this resource.
        orgAuthority.setDisplayName("updated-" + orgAuthority.getDisplayName());
        orgAuthority.setVocabType("updated-" + orgAuthority.getVocabType());
        if(logger.isDebugEnabled()){
            logger.debug("to be updated OrgAuthority");
            logger.debug(objectAsXmlString(orgAuthority, OrgauthoritiesCommon.class));
        }

        // Submit the updated resource to the service and store the response.
        MultipartOutput output = new MultipartOutput();
        OutputPart commonPart = output.addPart(orgAuthority, MediaType.APPLICATION_XML_TYPE);
        commonPart.getHeaders().add("label", client.getCommonPartName());
        res = client.update(knownResourceId, output);
        int statusCode = res.getStatus();

        // Check the status code of the response: does it match the expected response(s)?
        if(logger.isDebugEnabled()){
            logger.debug("update: status = " + statusCode);
        }
        Assert.assertTrue(REQUEST_TYPE.isValidStatusCode(statusCode),
                invalidStatusCodeMessage(REQUEST_TYPE, statusCode));
        Assert.assertEquals(statusCode, EXPECTED_STATUS_CODE);

        // Retrieve the updated resource and verify that its contents exist.
        input = (MultipartInput) res.getEntity();
        OrgauthoritiesCommon updatedOrgAuthority =
                (OrgauthoritiesCommon) extractPart(input,
                        client.getCommonPartName(), OrgauthoritiesCommon.class);
        Assert.assertNotNull(updatedOrgAuthority);

        // Verify that the updated resource received the correct data.
        Assert.assertEquals(updatedOrgAuthority.getDisplayName(),
                orgAuthority.getDisplayName(),
                "Data in updated object did not match submitted data.");
    }

    @Test(dataProvider="testName", dataProviderClass=AbstractServiceTestImpl.class,
        dependsOnMethods = {"readItem", "update"})
    public void updateItem(String testName) throws Exception {

        // Perform setup.
        setupUpdate(testName);

        ClientResponse<MultipartInput> res =
                client.readItem(knownResourceId, knownItemResourceId);
        if(logger.isDebugEnabled()){
            logger.debug(testName + ": read status = " + res.getStatus());
        }
        Assert.assertEquals(res.getStatus(), EXPECTED_STATUS_CODE);

        if(logger.isDebugEnabled()){
            logger.debug("got Organization to update with ID: " +
                knownItemResourceId +
                " in OrgAuthority: " + knownResourceId );
        }
        MultipartInput input = (MultipartInput) res.getEntity();
        OrganizationsCommon organization = (OrganizationsCommon) extractPart(input,
                client.getItemCommonPartName(), OrganizationsCommon.class);
        Assert.assertNotNull(organization);

        // Update the contents of this resource.
        organization.setShortName("updated-" + organization.getShortName());
        if(logger.isDebugEnabled()){
            logger.debug("to be updated Organization");
            logger.debug(objectAsXmlString(organization,
                OrganizationsCommon.class));
        }

        // Submit the updated resource to the service and store the response.
        MultipartOutput output = new MultipartOutput();
        OutputPart commonPart = output.addPart(organization, MediaType.APPLICATION_XML_TYPE);
        commonPart.getHeaders().add("label", client.getItemCommonPartName());
        res = client.updateItem(knownResourceId, knownItemResourceId, output);
        int statusCode = res.getStatus();

        // Check the status code of the response: does it match the expected response(s)?
        if(logger.isDebugEnabled()){
            logger.debug("updateItem: status = " + statusCode);
        }
        Assert.assertTrue(REQUEST_TYPE.isValidStatusCode(statusCode),
                invalidStatusCodeMessage(REQUEST_TYPE, statusCode));
        Assert.assertEquals(statusCode, EXPECTED_STATUS_CODE);

        // Retrieve the updated resource and verify that its contents exist.
        input = (MultipartInput) res.getEntity();
        OrganizationsCommon updatedOrganization =
                (OrganizationsCommon) extractPart(input,
                        client.getItemCommonPartName(), OrganizationsCommon.class);
        Assert.assertNotNull(updatedOrganization);

        // Verify that the updated resource received the correct data.
        Assert.assertEquals(updatedOrganization.getShortName(),
                organization.getShortName(),
                "Data in updated Organization did not match submitted data.");
    }

    // Failure outcomes
    // Placeholders until the three tests below can be uncommented.
    // See Issue CSPACE-401.
    @Override
    public void updateWithEmptyEntityBody(String testName) throws Exception {
    }

    @Override
    public void updateWithMalformedXml(String testName) throws Exception {
    }

    @Override
    public void updateWithWrongXmlSchema(String testName) throws Exception {
    }

    /*
    @Override
    @Test(dataProvider="testName", dataProviderClass=AbstractServiceTest.class,
        dependsOnMethods = {"create", "update", "testSubmitRequest"})
    public void updateWithEmptyEntityBody(String testName) throws Exception {

    // Perform setup.
    setupUpdateWithEmptyEntityBody(testName);

    // Submit the request to the service and store the response.
    String method = REQUEST_TYPE.httpMethodName();
    String url = getResourceURL(knownResourceId);
    String mediaType = MediaType.APPLICATION_XML;
    final String entity = "";
    int statusCode = submitRequest(method, url, mediaType, entity);

    // Check the status code of the response: does it match
    // the expected response(s)?
    if(logger.isDebugEnabled()){
        logger.debug(testName + ": url=" + url +
            " status=" + statusCode);
     }
    Assert.assertTrue(REQUEST_TYPE.isValidStatusCode(statusCode),
    invalidStatusCodeMessage(REQUEST_TYPE, statusCode));
    Assert.assertEquals(statusCode, EXPECTED_STATUS_CODE);
    }

    @Override
    @Test(dataProvider="testName", dataProviderClass=AbstractServiceTest.class,
        dependsOnMethods = {"create", "update", "testSubmitRequest"})
    public void updateWithMalformedXml(String testName) throws Exception {

    // Perform setup.
    setupUpdateWithMalformedXml(testName);

    // Submit the request to the service and store the response.
    String method = REQUEST_TYPE.httpMethodName();
    String url = getResourceURL(knownResourceId);
    String mediaType = MediaType.APPLICATION_XML;
    final String entity = MALFORMED_XML_DATA;
    int statusCode = submitRequest(method, url, mediaType, entity);

    // Check the status code of the response: does it match
    // the expected response(s)?
    if(logger.isDebugEnabled()){
        logger.debug(testName + ": url=" + url +
           " status=" + statusCode);
     }
    Assert.assertTrue(REQUEST_TYPE.isValidStatusCode(statusCode),
    invalidStatusCodeMessage(REQUEST_TYPE, statusCode));
    Assert.assertEquals(statusCode, EXPECTED_STATUS_CODE);
    }

    @Override
    @Test(dataProvider="testName", dataProviderClass=AbstractServiceTest.class,
        dependsOnMethods = {"create", "update", "testSubmitRequest"})
    public void updateWithWrongXmlSchema(String testName) throws Exception {

    // Perform setup.
    setupUpdateWithWrongXmlSchema(testName);

    // Submit the request to the service and store the response.
    String method = REQUEST_TYPE.httpMethodName();
    String url = getResourceURL(knownResourceId);
    String mediaType = MediaType.APPLICATION_XML;
    final String entity = WRONG_XML_SCHEMA_DATA;
    int statusCode = submitRequest(method, url, mediaType, entity);

    // Check the status code of the response: does it match
    // the expected response(s)?
    if(logger.isDebugEnabled()){
        logger.debug("updateWithWrongXmlSchema: url=" + url +
            " status=" + statusCode);
     }
    Assert.assertTrue(REQUEST_TYPE.isValidStatusCode(statusCode),
    invalidStatusCodeMessage(REQUEST_TYPE, statusCode));
    Assert.assertEquals(statusCode, EXPECTED_STATUS_CODE);
    }
     */


    @Override
    @Test(dataProvider="testName", dataProviderClass=AbstractServiceTestImpl.class,
        dependsOnMethods = {"update", "testSubmitRequest"})
    public void updateNonExistent(String testName) throws Exception {

        // Perform setup.
        setupUpdateNonExistent(testName);

        // Submit the request to the service and store the response.
        // Note: The ID used in this 'create' call may be arbitrary.
        // The only relevant ID may be the one used in update(), below.

        // The only relevant ID may be the one used in update(), below.
        MultipartOutput multipart = createOrgAuthorityInstance(NON_EXISTENT_ID);
        ClientResponse<MultipartInput> res =
                client.update(NON_EXISTENT_ID, multipart);
        int statusCode = res.getStatus();

        // Check the status code of the response: does it match
        // the expected response(s)?
        if(logger.isDebugEnabled()){
            logger.debug(testName + ": status = " + statusCode);
        }
        Assert.assertTrue(REQUEST_TYPE.isValidStatusCode(statusCode),
                invalidStatusCodeMessage(REQUEST_TYPE, statusCode));
        Assert.assertEquals(statusCode, EXPECTED_STATUS_CODE);
    }

    @Test(dataProvider="testName", dataProviderClass=AbstractServiceTestImpl.class,
        dependsOnMethods = {"updateItem", "testItemSubmitRequest"})
    public void updateNonExistentItem(String testName) throws Exception {

        // Perform setup.
        setupUpdateNonExistent(testName);

        // Submit the request to the service and store the response.
        // Note: The ID used in this 'create' call may be arbitrary.
        // The only relevant ID may be the one used in update(), below.

        // The only relevant ID may be the one used in update(), below.
        Map<String, String> nonexOrgMap = new HashMap<String,String>();
        nonexOrgMap.put(OrganizationJAXBSchema.SHORT_NAME, "Non-existent");
        String refName = OrgAuthorityClientUtils.createOrganizationRefName(knownResourceRefName, NON_EXISTENT_ID, true);
        MultipartOutput multipart = 
        	OrgAuthorityClientUtils.createOrganizationInstance(
        		knownResourceId, refName,
        		nonexOrgMap, client.getItemCommonPartName() );
        ClientResponse<MultipartInput> res =
                client.updateItem(knownResourceId, NON_EXISTENT_ID, multipart);
        int statusCode = res.getStatus();

        // Check the status code of the response: does it match
        // the expected response(s)?
        if(logger.isDebugEnabled()){
            logger.debug(testName + ": status = " + statusCode);
        }
        Assert.assertTrue(REQUEST_TYPE.isValidStatusCode(statusCode),
                invalidStatusCodeMessage(REQUEST_TYPE, statusCode));
        Assert.assertEquals(statusCode, EXPECTED_STATUS_CODE);
    }

    // ---------------------------------------------------------------
    // CRUD tests : DELETE tests
    // ---------------------------------------------------------------
    // Success outcomes
    @Override
    @Test(dataProvider="testName", dataProviderClass=AbstractServiceTestImpl.class,
        dependsOnMethods = {"create", "readList", "testSubmitRequest", "update"})
    public void delete(String testName) throws Exception {

        // Perform setup.
        setupDelete(testName);

        // Submit the request to the service and store the response.
        ClientResponse<Response> res = client.delete(knownResourceId);
        int statusCode = res.getStatus();

        // Check the status code of the response: does it match
        // the expected response(s)?
        if(logger.isDebugEnabled()){
            logger.debug(testName + ": status = " + statusCode);
        }
        Assert.assertTrue(REQUEST_TYPE.isValidStatusCode(statusCode),
                invalidStatusCodeMessage(REQUEST_TYPE, statusCode));
        Assert.assertEquals(statusCode, EXPECTED_STATUS_CODE);
    }

   @Test(dataProvider="testName", dataProviderClass=AbstractServiceTestImpl.class,
        dependsOnMethods = {"createItem", "readItemList", "testItemSubmitRequest",
            "updateItem", "verifyIllegalItemDisplayName"})
    public void deleteItem(String testName) throws Exception {

        // Perform setup.
        setupDelete(testName);

        // Submit the request to the service and store the response.
        ClientResponse<Response> res = client.deleteItem(knownResourceId, knownItemResourceId);
        int statusCode = res.getStatus();

        // Check the status code of the response: does it match
        // the expected response(s)?
        if(logger.isDebugEnabled()){
            logger.debug("delete: status = " + statusCode);
        }
        Assert.assertTrue(REQUEST_TYPE.isValidStatusCode(statusCode),
                invalidStatusCodeMessage(REQUEST_TYPE, statusCode));
        Assert.assertEquals(statusCode, EXPECTED_STATUS_CODE);
    }

    // Failure outcomes
    @Override
    @Test(dataProvider="testName", dataProviderClass=AbstractServiceTestImpl.class,
        dependsOnMethods = {"delete"})
    public void deleteNonExistent(String testName) throws Exception {

        // Perform setup.
        setupDeleteNonExistent(testName);

        // Submit the request to the service and store the response.
        ClientResponse<Response> res = client.delete(NON_EXISTENT_ID);
        int statusCode = res.getStatus();

        // Check the status code of the response: does it match
        // the expected response(s)?
        if(logger.isDebugEnabled()){
            logger.debug(testName + ": status = " + statusCode);
        }
        Assert.assertTrue(REQUEST_TYPE.isValidStatusCode(statusCode),
                invalidStatusCodeMessage(REQUEST_TYPE, statusCode));
        Assert.assertEquals(statusCode, EXPECTED_STATUS_CODE);
    }

    @Test(dataProvider="testName", dataProviderClass=AbstractServiceTestImpl.class,
        dependsOnMethods = {"deleteItem"})
    public void deleteNonExistentItem(String testName) {

        // Perform setup.
        setupDeleteNonExistent(testName);

        // Submit the request to the service and store the response.
        ClientResponse<Response> res = client.deleteItem(knownResourceId, NON_EXISTENT_ID);
        int statusCode = res.getStatus();

        // Check the status code of the response: does it match
        // the expected response(s)?
        if(logger.isDebugEnabled()){
            logger.debug(testName + ": status = " + statusCode);
        }
        Assert.assertTrue(REQUEST_TYPE.isValidStatusCode(statusCode),
                invalidStatusCodeMessage(REQUEST_TYPE, statusCode));
        Assert.assertEquals(statusCode, EXPECTED_STATUS_CODE);
    }

    // ---------------------------------------------------------------
    // Utility tests : tests of code used in tests above
    // ---------------------------------------------------------------
    /**
     * Tests the code for manually submitting data that is used by several
     * of the methods above.
     */
    @Test(dependsOnMethods = {"create", "read"})
    public void testSubmitRequest() {

        // Expected status code: 200 OK
        final int EXPECTED_STATUS = Response.Status.OK.getStatusCode();

        // Submit the request to the service and store the response.
        String method = ServiceRequestType.READ.httpMethodName();
        String url = getResourceURL(knownResourceId);
        int statusCode = submitRequest(method, url);

        // Check the status code of the response: does it match
        // the expected response(s)?
        if(logger.isDebugEnabled()){
            logger.debug("testSubmitRequest: url=" + url +
                " status=" + statusCode);
        }
        Assert.assertEquals(statusCode, EXPECTED_STATUS);

    }

    @Test(dependsOnMethods = {"createItem", "readItem", "testSubmitRequest"})
    public void testItemSubmitRequest() {

        // Expected status code: 200 OK
        final int EXPECTED_STATUS = Response.Status.OK.getStatusCode();

        // Submit the request to the service and store the response.
        String method = ServiceRequestType.READ.httpMethodName();
        String url = getItemResourceURL(knownResourceId, knownItemResourceId);
        int statusCode = submitRequest(method, url);

        // Check the status code of the response: does it match
        // the expected response(s)?
        if(logger.isDebugEnabled()){
            logger.debug("testItemSubmitRequest: url=" + url +
                " status=" + statusCode);
        }
        Assert.assertEquals(statusCode, EXPECTED_STATUS);

    }

    // ---------------------------------------------------------------
    // Cleanup of resources created during testing
    // ---------------------------------------------------------------
    
    /**
     * Deletes all resources created by tests, after all tests have been run.
     *
     * This cleanup method will always be run, even if one or more tests fail.
     * For this reason, it attempts to remove all resources created
     * at any point during testing, even if some of those resources
     * may be expected to be deleted by certain tests.
     */
    @AfterClass(alwaysRun=true)
    public void cleanUp() {
        if (logger.isDebugEnabled()) {
            logger.debug("Cleaning up temporary resources created for testing ...");
        }
        // Clean up organization resources.
        String orgAuthorityResourceId;
        String organizationResourceId;
        for (Map.Entry<String, String> entry : allResourceItemIdsCreated.entrySet()) {
            organizationResourceId = entry.getKey();
            orgAuthorityResourceId = entry.getValue();
            // Note: Any non-success responses are ignored and not reported.
            ClientResponse<Response> res =
                client.deleteItem(orgAuthorityResourceId, organizationResourceId);
        }
        // Clean up orgAuthority resources.
        for (String resourceId : allResourceIdsCreated) {
            // Note: Any non-success responses are ignored and not reported.
            ClientResponse<Response> res = client.delete(resourceId);
        }
    }

    // ---------------------------------------------------------------
    // Utility methods used by tests above
    // ---------------------------------------------------------------
    @Override
    public String getServicePathComponent() {
        return SERVICE_PATH_COMPONENT;
    }

    public String getItemServicePathComponent() {
        return ITEM_SERVICE_PATH_COMPONENT;
    }

    /**
     * Returns the root URL for a service.
     *
     * This URL consists of a base URL for all services, followed by
     * a path component for the owning orgAuthority, followed by the 
     * path component for the items.
     *
     * @return The root URL for a service.
     */
    protected String getItemServiceRootURL(String parentResourceIdentifier) {
        return getResourceURL(parentResourceIdentifier) + "/" + getItemServicePathComponent();
    }

    /**
     * Returns the URL of a specific resource managed by a service, and
     * designated by an identifier (such as a universally unique ID, or UUID).
     *
     * @param  resourceIdentifier  An identifier (such as a UUID) for a resource.
     *
     * @return The URL of a specific resource managed by a service.
     */
    protected String getItemResourceURL(String parentResourceIdentifier, String resourceIdentifier) {
        return getItemServiceRootURL(parentResourceIdentifier) + "/" + resourceIdentifier;
    }

    private MultipartOutput createOrgAuthorityInstance(String identifier) {
    	String displayName = "displayName-" + identifier;
    	String refName = OrgAuthorityClientUtils.createOrgAuthRefName(displayName, true);
        return OrgAuthorityClientUtils.createOrgAuthorityInstance(
				displayName, refName, 
				client.getCommonPartName());
    }
}
