package org.bimserver.changes;

/******************************************************************************
 * Copyright (C) 2009-2019  BIMserver.org
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see {@literal<http://www.gnu.org/licenses/>}.
 *****************************************************************************/

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bimserver.BimserverDatabaseException;
import org.bimserver.database.BimserverLockConflictException;
import org.bimserver.database.queries.QueryObjectProvider;
import org.bimserver.database.queries.om.Query;
import org.bimserver.database.queries.om.QueryException;
import org.bimserver.database.queries.om.QueryPart;
import org.bimserver.emf.PackageMetaData;
import org.bimserver.shared.HashMapVirtualObject;
import org.bimserver.shared.exceptions.UserException;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EReference;

public class RemoveAllReferencesChange implements Change {

	private final Long oid;
	private final String referenceName;

	public RemoveAllReferencesChange(Long oid, String referenceName) {
		this.oid = oid;
		this.referenceName = referenceName;
	}

	@SuppressWarnings("rawtypes")
	@Override
	public void execute(Transaction transaction) throws UserException, BimserverLockConflictException,
			BimserverDatabaseException, IOException, QueryException {
		PackageMetaData packageMetaData = transaction.getDatabaseSession().getMetaDataManager().getPackageMetaData(transaction.getProject().getSchema());

		HashMapVirtualObject object = transaction.get(oid);
		if (object == null) {
			Query query = new Query(packageMetaData);
			QueryPart queryPart = query.createQueryPart();
			queryPart.addOid(oid);

			QueryObjectProvider queryObjectProvider = new QueryObjectProvider(transaction.getDatabaseSession(), transaction.getBimServer(), query, Collections.singleton(transaction.getPreviousRevision().getOid()), packageMetaData);
			object = queryObjectProvider.next();
		}
		
		EClass eClass = transaction.getDatabaseSession().getEClassForOid(oid);
		if (!ChangeHelper.canBeChanged(eClass)) {
			throw new UserException("Only objects from the following schemas are allowed to be changed: Ifc2x3tc1 and IFC4, this object (" + eClass.getName() + ") is from the \"" + eClass.getEPackage().getName() + "\" package");
		}
		if (object == null) {
			throw new UserException("No object of type \"" + eClass.getName() + "\" with oid " + oid + " found in project with pid " + transaction.getProject().getId());
		}
		EReference eReference = packageMetaData.getEReference(eClass.getName(), referenceName);
		if (eReference == null) {
			throw new UserException("No reference with the name \"" + referenceName + "\" found in class \"" + eClass.getName() + "\"");
		}
		if (!eReference.isMany()) {
			throw new UserException("Reference is not of type 'many'");
		}
		List list = (List) object.get(eReference.getName());
		List<Long> referencedOids = new ArrayList<>();
		while (!list.isEmpty()) {
			referencedOids.add((long) list.get(0));
			list.remove(0);
		}
		transaction.updated(object);

		// fix inverses, e.g. IfcRelDefinesByProperties.RelatedObjects SET [1:?], inverse IfcObject.IsDefinedBy SET [0:?]
		for(long referencedOid : referencedOids){
			EClass eClassForOid = transaction.getDatabaseSession().getEClassForOid(referencedOid);
			EReference inverseOrOpposite = packageMetaData.getInverseOrOpposite(eClassForOid, eReference);
			if (inverseOrOpposite != null) {
				HashMapVirtualObject referencedObject = transaction.get(referencedOid); // we don't get the deleted
				if (referencedObject == null) {
					Query query = new Query(packageMetaData);
					QueryPart queryPart = query.createQueryPart();
					queryPart.addOid(referencedOid);

					QueryObjectProvider queryObjectProvider = new QueryObjectProvider(transaction.getDatabaseSession(), transaction.getBimServer(), query, Collections.singleton(transaction.getPreviousRevision().getOid()), packageMetaData);
					referencedObject = queryObjectProvider.next();
				}
				if (inverseOrOpposite.isMany()) {
					referencedObject.removeReference(inverseOrOpposite, referencedOid);
				} else {
					referencedObject.eUnset(inverseOrOpposite);
				}
				transaction.updated(referencedObject);
			}
		}
	}
}
