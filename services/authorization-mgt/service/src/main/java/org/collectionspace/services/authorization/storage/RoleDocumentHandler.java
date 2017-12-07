/**
 *  This document is a part of the source code and related artifacts
 *  for CollectionSpace, an open source collections management system
 *  for museums and related institutions:

 *  http://www.collectionspace.org
 *  http://wiki.collectionspace.org

 *  Copyright 2009 University of California at Berkeley

 *  Licensed under the Educational Community License (ECL), Version 2.0.
 *  You may not use this file except in compliance with this License.

 *  You may obtain a copy of the ECL 2.0 License at

 *  https://source.collectionspace.org/collection-space/LICENSE.txt

 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.collectionspace.services.authorization.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.collectionspace.services.authorization.PermissionRole;
import org.collectionspace.services.authorization.PermissionRoleSubResource;
import org.collectionspace.services.authorization.PermissionValue;
import org.collectionspace.services.authorization.Role;
import org.collectionspace.services.authorization.RoleValue;
import org.collectionspace.services.authorization.RolesList;
import org.collectionspace.services.authorization.SubjectType;

import org.collectionspace.services.client.PermissionRoleFactory;
import org.collectionspace.services.client.RoleClient;
import org.collectionspace.services.client.RoleFactory;

import org.collectionspace.services.common.document.BadRequestException;
import org.collectionspace.services.common.document.DocumentFilter;
import org.collectionspace.services.common.document.DocumentWrapper;
import org.collectionspace.services.common.document.JaxbUtils;
import org.collectionspace.services.common.security.SecurityUtils;
import org.collectionspace.services.common.storage.jpa.JpaDocumentHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Document handler for Role
 * @author 
 */
public class RoleDocumentHandler
		extends JpaDocumentHandler<Role, RolesList, Role, List> {
    private final Logger logger = LoggerFactory.getLogger(RoleDocumentHandler.class);
    private Role role;
    private RolesList rolesList;

    @Override
    public void handleCreate(DocumentWrapper<Role> wrapDoc) throws Exception {
        String id = UUID.randomUUID().toString();
        Role role = wrapDoc.getWrappedObject();
        
        // Synthesize the display name if it was not passed in.
        String displayName = role.getDisplayName();
        boolean displayNameEmpty = true;
        if (displayName != null) {
        	displayNameEmpty = displayName.trim().isEmpty();    	
        }
        if (displayNameEmpty == true) {
        	role.setDisplayName(role.getRoleName());
        }
        
        setTenant(role);
        role.setRoleName(RoleClient.getBackendRoleName(role.getRoleName(), role.getTenantId()));
        role.setCsid(id);
        // We do not allow creation of locked roles through the services.
        role.setMetadataProtection(null);
        role.setPermsProtection(null);
    }
    
    @Override
    public void completeCreate(DocumentWrapper<Role> wrapDoc) throws Exception {
    	Role role = wrapDoc.getWrappedObject();
    	List<PermissionValue> permValueList = role.getPermission();
    	if (permValueList != null && permValueList.size() > 0) {
    		// create and persist a permrole instance
    		// The caller of this method needs to ensure a valid and active EM (EntityManager) instance is in the Service context
    		RoleValue roleValue = RoleFactory.createRoleValueInstance(role);
    		PermissionRole permRole = PermissionRoleFactory.createPermissionRoleInstance(SubjectType.PERMISSION, roleValue,
    				permValueList, true, true);
            PermissionRoleSubResource subResource =
                    new PermissionRoleSubResource(PermissionRoleSubResource.ROLE_PERMROLE_SERVICE);
            String permrolecsid = subResource.createPermissionRole(permRole, SubjectType.PERMISSION);
    	}
    }

    @Override
    public void handleUpdate(DocumentWrapper<Role> wrapDoc) throws Exception {
        Role roleFound = wrapDoc.getWrappedObject();
        Role roleReceived = getCommonPart();
        // If marked as metadata immutable, do not do update
        if(!RoleClient.IMMUTABLE.equals(roleFound.getMetadataProtection())) {
	        roleReceived.setRoleName(RoleClient.getBackendRoleName(roleReceived.getRoleName(),
	        		roleFound.getTenantId()));
	        merge(roleReceived, roleFound);
        }
    }

    /**
     * Merge fields manually from 'from' to the 'to' role
     * -this method is created due to inefficiency of JPA EM merge
     * @param from
     * @param to
     * @return merged role
     */
    private Role merge(Role from, Role to) throws Exception {
        // A role's name cannot be changed
        if (!(from.getRoleName().equalsIgnoreCase(to.getRoleName()))) {
            String msg = "Role name cannot be changed " + to.getRoleName();
            logger.error(msg);
            throw new BadRequestException(msg);
        }
        
        if (from.getDisplayName() != null && !from.getDisplayName().trim().isEmpty() ) {
        	to.setDisplayName(from.getDisplayName());
        }
        if (from.getRoleGroup() != null && !from.getRoleGroup().trim().isEmpty()) {
            to.setRoleGroup(from.getRoleGroup());
        }
        if (from.getDescription() != null && !from.getDescription().trim().isEmpty()) {
            to.setDescription(from.getDescription());
        }

        if (logger.isDebugEnabled()) {
        	org.collectionspace.services.authorization.ObjectFactory objectFactory =
        		new org.collectionspace.services.authorization.ObjectFactory();
            logger.debug("Merged role on update=" + JaxbUtils.toString(objectFactory.createRole(to), Role.class));
        }
        
        return to;
    }

    @Override
    public void completeUpdate(DocumentWrapper<Role> wrapDoc) throws Exception {
        Role upAcc = wrapDoc.getWrappedObject();
        getServiceContext().setOutput(upAcc);
        sanitize(upAcc);
    }

    @Override
    public void handleGet(DocumentWrapper<Role> wrapDoc) throws Exception {
        setCommonPart(extractCommonPart(wrapDoc));
        sanitize(getCommonPart());
        getServiceContext().setOutput(role);
    }

    @Override
    public void handleGetAll(DocumentWrapper<List> wrapDoc) throws Exception {
        RolesList rolesList = extractCommonPartList(wrapDoc);
        setCommonPartList(rolesList);
        getServiceContext().setOutput(getCommonPartList());
    }

    @Override
    public Role extractCommonPart(
            DocumentWrapper<Role> wrapDoc)
            throws Exception {
        return wrapDoc.getWrappedObject();
    }

    @Override
    public void fillCommonPart(Role obj, DocumentWrapper<Role> wrapDoc)
            throws Exception {
        throw new UnsupportedOperationException("operation not relevant for AccountDocumentHandler");
    }

    @Override
    public RolesList extractCommonPartList(
            DocumentWrapper<List> wrapDoc)
            throws Exception {

        RolesList rolesList = new RolesList();
        List<Role> list = new ArrayList<Role>();
        rolesList.setRole(list);
        for (Object obj : wrapDoc.getWrappedObject()) {
            Role role = (Role) obj;
            sanitize(role);
            list.add(role);
        }
        return rolesList;
    }

    @Override
    public Role getCommonPart() {
        return role;
    }

    @Override
    public void setCommonPart(Role role) {
        this.role = role;
    }

    @Override
    public RolesList getCommonPartList() {
        return rolesList;
    }

    @Override
    public void setCommonPartList(RolesList rolesList) {
        this.rolesList = rolesList;
    }

    @Override
    public String getQProperty(
            String prop) {
        return null;
    }

    @Override
    public DocumentFilter createDocumentFilter() {
        DocumentFilter filter = new RoleJpaFilter(this.getServiceContext());
        return filter;
    }

    /**
     * sanitize removes data not needed to be sent to the consumer
     * @param roleFound
     */
    private void sanitize(Role role) {
        if (!SecurityUtils.isCSpaceAdmin()) {
            role.setTenantId(null); // REM - See no reason for hiding the tenant ID?
        }
    }

    private void setTenant(Role role) {
        //set tenant only if not available from input
        if (role.getTenantId() == null || role.getTenantId().isEmpty()) {
            role.setTenantId(getServiceContext().getTenantId());
        }
    }
}
