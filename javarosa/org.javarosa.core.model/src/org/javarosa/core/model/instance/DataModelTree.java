package org.javarosa.core.model.instance;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.Enumeration;
import java.util.Vector;

import org.javarosa.core.model.IDataReference;
import org.javarosa.core.model.IFormDataModel;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.instance.utils.ITreeVisitor;
import org.javarosa.core.model.utils.IDataModelVisitor;
import org.javarosa.core.services.storage.utilities.IDRecordable;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.ExtWrapNullable;
import org.javarosa.core.util.externalizable.PrototypeFactory;

public class DataModelTree implements IFormDataModel, IDRecordable {

	/** The root of this tree */
	private TreeElement root;
	//represents '/'; always has one and only one child -- the top-level instance data node
	//this node is never returned or manipulated directly
	
	/** The name for this data model */
	private String name;
	
	/** The integer Id of the model */
	private int id;
	
	/** The ID of the form that this is a model for */
	private int formId;
	
	/** The date that this model was taken and recorded */
	private Date dateSaved;
	
	public String schema;
	
	public DataModelTree() { }
	
	/**
	 * Creates a new data model using the root given.
	 * 
	 * @param root The root of the tree for this data model.
	 */
	public DataModelTree(TreeElement root) {
		setRoot(root);
	}
	
	/**
	 * Sets the root element of this Model's tree
	 * @param root The root of the tree for this data model.
	 */
	public void setRoot(TreeElement topLevel) {
		root = new TreeElement(null, 0);
		if (topLevel != null)
			root.addChild(topLevel);
	}
	
	/**
	 * @return This model's root tree element
	 */
	public TreeElement getRoot() {
		if (root == null)
			return null;
		else if (root.getNumChildren() == 0)
			return null;
		else	
			return (TreeElement)root.getChildren().elementAt(0);
	}

	//throws classcastexception if not using XPathReference
	public static TreeReference unpackReference (IDataReference ref) {
		return (TreeReference)ref.getReference();
	}	
	
	public boolean deleteNode (IDataReference ref) {
		return false;
	}
	
	public boolean copyNode (TreeReference from, TreeReference to) {
		if (!from.isAbsolute())
			return false;
		
		TreeElement src = resolveReference(from);
		if (src == null)
			return false; //source does not exist
		
		return copyNode(src, to);
	}
	
	//for making new repeat instances; 'from' and 'to' must be unambiguous references EXCEPT 'to' may be ambiguous at its final step
	//return true is successfully copied, false otherwise
	public boolean copyNode (TreeElement src, TreeReference to) {
		if (!to.isAbsolute())
			return false;
			
		//strip out dest node info and get dest parent
		String dstName = (String)to.names.lastElement();
		int dstMult = ((Integer)to.multiplicity.lastElement()).intValue();
		TreeReference toParent = to.getParentRef();
		
		TreeElement parent = resolveReference(toParent);
		if (parent == null)
			return false; //dest parent does not exist
		if (!parent.isChildable())
			return false; //dest parent is an unfit parent
		
		if (dstMult == TreeReference.INDEX_UNBOUND) {
			dstMult = parent.getChildMultiplicity(dstName);
		} else if (parent.getChild(dstName, dstMult) != null) {
			return false; //dest node already exists
		}
		
		TreeElement dest = src.deepCopy(false);
		dest.setName(dstName);
		dest.multiplicity = dstMult;
		parent.addChild(dest);
		return true;
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see org.javarosa.core.model.IFormDataModel#updateDataValue(IDataBinding,
	 * Object)
	 */
	//don't think this is used anymore
	public boolean updateDataValue(IDataReference questionBinding, IAnswerData value) {
		TreeElement treeElement = resolveReference(questionBinding);
		if (treeElement != null) {
			treeElement.setValue(value);
			return true;
		} else {
			return false;
		}
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.javarosa.core.model.IFormDataModel#getDataValue(org.javarosa.core.model.IDataReference)
	 */
	//don't think this is used anymore
	public IAnswerData getDataValue(IDataReference questionReference) {
		TreeElement element = resolveReference(questionReference);
		if(element != null) {
			return element.getValue();
		}
		else {
			return null;
		}
	}

	//take a ref that unambiguously refers to a single node and return that node
	//return null if ref is ambiguous, node does not exist, ref is relative, or ref is '/'
	//can be used to retrieve template nodes
	public TreeElement resolveReference (TreeReference ref) {
		if (!ref.isAbsolute())
			return null;
		
		TreeElement node = root;
		for (int i = 0; i < ref.size(); i++) {
			String name = (String)ref.names.elementAt(i);
			int mult = ((Integer)ref.multiplicity.elementAt(i)).intValue();
			if (mult == TreeReference.INDEX_UNBOUND) {
				if (node.getChildMultiplicity(name) == 1) {
					mult = 0;
				} else {
					//reference is not unambiguous
					node = null;
					break;
				}
			}
			
			node = node.getChild(name, mult);
			if (node == null)
				break;
		}
		return (node == root ? null : node); //never return a reference to '/'
	}

	//same as resolveReference but return a vector containing all interstitial nodes: top-level instance data node first, and target node last
	//returns null in all the same situations as resolveReference EXCEPT ref '/' will instead return empty vector
	public Vector explodeReference (TreeReference ref) {
		if (!ref.isAbsolute())
			return null;
		
		Vector nodes = new Vector();
		TreeElement cur = root;
		for (int i = 0; i < ref.size(); i++) {
			String name = (String)ref.names.elementAt(i);
			int mult = ((Integer)ref.multiplicity.elementAt(i)).intValue();
			if (mult == TreeReference.INDEX_UNBOUND) {
				if (cur.getChildMultiplicity(name) == 1) {
					mult = 0;
				} else {
					//reference is not unambiguous
					return null;
				}
			}
			
			if (cur != root) {
				nodes.addElement(cur);
			}
			
			cur = cur.getChild(name, mult);
			if (cur == null) {
				return null;
			}
		}
		return nodes;		
	}

	public Vector expandReference (TreeReference ref) {
		return expandReference(ref, false);
	}
	
	//take in a potentially-ambiguous ref, and return a vector of refs for all nodes that match the passed-in ref
	//meaning, search out all repeated nodes that match the pattern of the passed-in ref
	//every ref in the returned vector will be unambiguous (no index will ever be INDEX_UNBOUND)
	//does not return template nodes when matching INDEX_UNBOUND, but will match templates when INDEX_TEMPLATE is explicitly set
	//return null if ref is relative, otherwise return vector of refs (but vector will be empty is no refs match)
	//'/' returns {'/'}
	//can handle sub-repetitions (e.g., {/a[1]/b[1], /a[1]/b[2], /a[2]/b[1]})
	public Vector expandReference (TreeReference ref, boolean includeTemplates) {
		if (!ref.isAbsolute())
			return null;
		
		Vector v = new Vector();
		expandReference(ref, TreeReference.rootRef(), root, v, includeTemplates);
		return v;
	}
	
	//recursive helper function for expandReference
	//sourceRef: original path we're matching against
	//node: current node that has matched the sourceRef thus far
	//templateRef: explicit path that refers to the current node
	//refs: Vector to collect matching paths; if 'node' is a target node that matches sourceRef, templateRef is added to refs
	private void expandReference (TreeReference sourceRef, TreeReference templateRef, TreeElement node, Vector refs, boolean includeTemplates) {
		int depth = templateRef.size();
		
		if (depth == sourceRef.size()) {
			refs.addElement(templateRef);			
		} else if (node.getNumChildren() > 0) {
			String name = (String)sourceRef.names.elementAt(depth);
			int mult = ((Integer)sourceRef.multiplicity.elementAt(depth)).intValue();

			Vector children = new Vector();
			if (mult == TreeReference.INDEX_UNBOUND) {
				int count = node.getChildMultiplicity(name);
				for (int i = 0; i < count; i++) {
					TreeElement child = node.getChild(name, i);
					if (child != null) {
						children.addElement(child);
					} else {
						throw new IllegalStateException(); //missing/non-sequential nodes
					}
				}
				if (includeTemplates) {
					TreeElement template = node.getChild(name, TreeReference.INDEX_TEMPLATE);
					if (template != null) {
						children.addElement(template);
					}
				}
			} else {
				TreeElement child = node.getChild(name, mult);
				if (child != null)
					children.addElement(child);
			}
			
			for (Enumeration e = children.elements(); e.hasMoreElements(); ) {
				TreeElement child = (TreeElement)e.nextElement();
				TreeReference newTemplateRef = (children.size() == 1 ? templateRef : templateRef.clone()); //don't clone templateRef unnecessarily
				newTemplateRef.names.addElement(name);
				newTemplateRef.multiplicity.addElement(new Integer(child.getMult()));
				
				expandReference(sourceRef, newTemplateRef, child, refs, includeTemplates);
			}
		}
	}
	
	//retrieve the template node for a given repeated node
	//ref may be ambiguous
	//return null if node is not repeatable
	//assumes templates are built correctly and obey all data model validity rules
	public TreeElement getTemplate (TreeReference ref) {
		TreeElement node = getTemplatePath(ref);
		return (node == null ? null : node.repeatable ? node : null);
	}
	
	public TreeElement getTemplatePath (TreeReference ref) {
		if (!ref.isAbsolute())
			return null;
		
		TreeElement node = root;
		for (int i = 0; i < ref.size(); i++) {
			String name = (String)ref.names.elementAt(i);

			TreeElement newNode = node.getChild(name, TreeReference.INDEX_TEMPLATE);			
			if (newNode == null)
				newNode = node.getChild(name, 0);
			if (newNode == null)
				return null;
			node = newNode;
		}
		
		return node;
	}
	
	//determine if nodes are homogeneous, meaning their descendant structure is 'identical' for repeat purposes
	//identical means all children match, and the children's children match, and so on
	//repeatable children are ignored; as they do not have to exist in the same quantity for nodes to be homogeneous
	//however, the child repeatable nodes MUST be verified amongst themselves for homogeneity later
	//this function ignores the names of the two nodes
	public static boolean isHomogeneous (TreeElement a, TreeElement b) {
		if (a.isLeaf() && b.isLeaf()) {
			return true;
		} else if (a.isChildable() && b.isChildable()) {
			//verify that every (non-repeatable) node in a exists in b and vice versa
			for (int k = 0; k < 2; k++) {
				TreeElement n1 = (k == 0 ? a : b);
				TreeElement n2 = (k == 0 ? b : a);
			
				for (int i = 0; i < n1.getNumChildren(); i++) {
					TreeElement child1 = (TreeElement)n1.getChildren().elementAt(i);
					if (child1.repeatable)
						continue;
					TreeElement child2 = n2.getChild(child1.getName(), 0);
					if (child2 == null)
						return false;
					if (child2.repeatable)
						throw new RuntimeException("shouldn't happen");
				}
			}

			//compare children
			for (int i = 0; i < a.getNumChildren(); i++) {
				TreeElement childA = (TreeElement)a.getChildren().elementAt(i);
				if (childA.repeatable)
					continue;
				TreeElement childB = b.getChild(childA.getName(), 0);
				if (!isHomogeneous(childA, childB))
					return false;
			}
			
			return true;
		} else {
			return false;
		}
	}
	
	/**
	 * Resolves a binding to a particular question data element
	 * 
	 * @param binding The binding representing a particular question
	 * @return A QuestionDataElement corresponding to the binding
	 * provided. Null if none exists in this tree.
	 */
	public TreeElement resolveReference(IDataReference binding) {
		return resolveReference(unpackReference(binding));
	}
	
	public void accept(IDataModelVisitor visitor) {
		visitor.visit(this);
		if(root != null) {
			if(visitor instanceof ITreeVisitor) {
				root.accept((ITreeVisitor)visitor);
			}
		}
	}
	
	public void setDateSaved(Date dateSaved) {
		this.dateSaved = dateSaved;
	}
	
	public void setFormId(int formId) {
		this.formId = formId;
	}
	
	/* (non-Javadoc)
	 * @see org.javarosa.core.model.IFormDataModel#getDateSaved()
	 */
	public Date getDateSaved() {
		return this.dateSaved;
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.model.IFormDataModel#getFormReferenceId()
	 */
	public int getFormId() {
		return this.formId;
	}

	/*
	 * (non-Javadoc)
	 * @see org.javarosa.core.services.storage.utilities.Externalizable#readExternal(java.io.DataInputStream)
	 */
	public void readExternal(DataInputStream in, PrototypeFactory pf) throws IOException, DeserializationException {
		id = ExtUtil.readInt(in);
		formId = ExtUtil.readInt(in);
		name = (String)ExtUtil.read(in, new ExtWrapNullable(String.class), pf);
		//schema = (String)ExtUtil.read(in, new ExtWrapNullable(String.class), pf);
		dateSaved = (Date)ExtUtil.read(in, new ExtWrapNullable(Date.class), pf);
		setRoot((TreeElement)ExtUtil.read(in, TreeElement.class, pf));
	}

	/*
	 * (non-Javadoc)
	 * @see org.javarosa.core.services.storage.utilities.Externalizable#writeExternal(java.io.DataOutputStream)
	 */
	public void writeExternal(DataOutputStream out) throws IOException {
		ExtUtil.writeNumeric(out, id);
		ExtUtil.writeNumeric(out, formId);
		ExtUtil.write(out, new ExtWrapNullable(name));
		//ExtUtil.write(out, new ExtWrapNullable(schema));
		ExtUtil.write(out, new ExtWrapNullable(dateSaved));		
		ExtUtil.write(out, getRoot());
	}

	/*
	 * (non-Javadoc)
	 * @see org.javarosa.core.model.IFormDataModel#getName()
	 */
	public String getName() {
		return name;
	}

	/**
	 * Sets the name of this datamodel instance
	 * @param name The name to be used
	 */
	public void setName(String name) {
		this.name = name;
	}

	/*
	 * (non-Javadoc)
	 * @see org.javarosa.core.model.IFormDataModel#getId()
	 */
	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}
	
	//treating id and record id as the same until we resolve the need for both of them
	public int getRecordId () {
		return getId();
	}
	
	public void setRecordId(int recordId) {
		setId(recordId);
	}

	public TreeReference addNode (TreeReference ambigRef) {
		TreeReference ref = ambigRef.clone();
		if (createNode(ref) != null) {
			return ref;
		} else {
			return null;
		}
	}
	
	public TreeReference addNode (TreeReference ambigRef, IAnswerData data, int dataType) {
		TreeReference ref = ambigRef.clone();
		TreeElement node = createNode(ref);
		if (node != null) {
			if (dataType >= 0) {
				node.dataType = dataType;
			}

			node.setValue(data);
			return ref;
		} else {
			return null;
		}
	}
	
	/*
	 * create the specified node in the tree, creating all intermediary nodes
	 * at each step, if necessary. if specified node already exists, return null
	 * 
	 * creating a duplicate node is only allowed at the final step. it will be done
	 * if the multiplicity of the last step is ALL or equal to the count of nodes
	 * already there
	 * 
	 * at intermediate steps, the specified existing node is used; if multiplicity is ALL:
	 * if no nodes exist, a new one is created; if one node exists, it is used; if multiple
	 * nodes exist, it's an error
	 *
	 * return the newly-created node; modify ref so that it's an unambiguous ref to the node
	 */
	private TreeElement createNode (TreeReference ref) {
		if (root == null) {
			root = new TreeElement(null, 0);
		}
		
		TreeElement node = root;
		
		for (int k = 0; k < ref.size(); k++) {
			String name = (String)ref.names.elementAt(k);
			int count = node.getChildMultiplicity(name);
			int mult = ((Integer)ref.multiplicity.elementAt(k)).intValue();
			
			TreeElement child;
			if (k < ref.size() - 1) {
				if (mult == TreeReference.INDEX_UNBOUND) {
					if (count > 1) {
						return null; //don't know which node to use
					} else {
						//will use existing (if one and only one) or create new
						mult = 0;
						ref.multiplicity.setElementAt(new Integer(0), k);
					}
				}
				
				//fetch
				child = node.getChild(name, mult);
				if (child == null) {
					if (mult == 0) {
						//create
						child = new TreeElement(name, count);
						node.addChild(child);
						ref.multiplicity.setElementAt(new Integer(count), k);
					} else {
						return null; //intermediate node does not exist
					}
				}
			} else {
				if (mult == TreeReference.INDEX_UNBOUND || mult == count) {
					if (k == 0 && root.getNumChildren() != 0) {
						return null; //can only be one top-level node, and it already exists
					}
					
					if (!node.isChildable()) {
						return null; //current node can't have children
					}
					
					//create new
					child = new TreeElement(name, count);
					node.addChild(child);
					ref.multiplicity.setElementAt(new Integer(count), k);
				} else {
					return null; //final node must be a newly-created node
				}
			}
		
			node = child;
		}
		
		return node;
	}
	
	
//	private TreeElement createNode (TreeReference ref) {
//		if (root == null) {
//			root = new TreeElement(null, 0);
//		}
//		
//		TreeElement node = root;
//		
//		for (int k = 0; k < ref.size(); k++) {
//			String name = (String)ref.names.elementAt(k);
//			int count = node.getChildMultiplicity(name);
//			int mult = ((Integer)ref.multiplicity.elementAt(k)).intValue();
//			
//			TreeElement child;
//			if (mult >= 0 && mult < count) {
//				if (k == ref.size() - 1) {
//					return null; //final node must be a newly-created node
//				}
//				
//				//fetch existing
//				child = node.getChild(name, mult);
//				if (child == null) {
//					return null; //intermediate node does not exist, and not specified in a way that will cause it to be created
//				}
//			} else if (mult == TreeReference.INDEX_UNBOUND || mult == count) {
//				if (k == 0 && root.getNumChildren() != 0) {
//					return null; //can only be one top-level node, and it already exists
//				}
//				
//				if (!node.isChildable()) {
//					return null; //current node can't have children
//				}
//				
//				//create new
//				child = new TreeElement(name, count);
//				node.addChild(child);
//				ref.multiplicity.setElementAt(new Integer(count), k);
//			} else {
//				return null;
//			}
//		
//			node = child;
//		}
//		
//		return node;
//	}

}

