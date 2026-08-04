package com.cabolabs.openehr.opt.cli.services

import com.cabolabs.openehr.opt.diff.SemanticOperationalTemplateDiff
import com.cabolabs.openehr.opt.diff.SemanticNodeDiff
import com.cabolabs.openehr.opt.diff.AttributeDiff
import com.cabolabs.openehr.opt.diff.FieldChange
import com.cabolabs.openehr.opt.diff.ListChange
import com.cabolabs.openehr.opt.diff.OperationalTemplateDiff
import com.cabolabs.openehr.opt.diff.NodeDiff

// Renders SemanticOperationalTemplateDiff / OperationalTemplateDiff trees (from openEHR-SDK's
// com.cabolabs.openehr.opt.diff) as either an indented terminal tree or a plain (non-circular)
// Map suitable for JSON serialization. Never serializes NodeDiff.optNode or
// SemanticNodeDiff.node1/node2 directly - those carry circular parent pointers.
class OptDiffService {

   private static final Map PREFIX = [added: '+', removed: '-', modified: '~', same: ' ']
   private static final Map COLOR  = [added: '[32m', removed: '[31m', modified: '[33m', same: '[2m']
   private static final String RESET = '[0m'

   // ---------------------------------------------------------------------
   // Semantic (field-level) rendering
   // ---------------------------------------------------------------------

   static String renderSemantic(SemanticOperationalTemplateDiff diff, boolean full, boolean color) {
      def sb = new StringBuilder()
      def summary = [added: 0, removed: 0, modified: 0, same: 0]

      sb << "Semantic diff (field-level - shows added/removed/modified constraints)\n"

      if (diff.templateMetadataChanges) {
         sb << "\nTemplate metadata changes:\n"
         diff.templateMetadataChanges.each {
            sb << "  ${it.field}: ${fmt(it.oldValue)} -> ${fmt(it.newValue)}\n"
         }
      }

      sb << "\n"
      renderSemanticNode(diff.root, 0, full, color, sb, summary)
      sb << "\n${summary.added} added, ${summary.removed} removed, ${summary.modified} modified, ${summary.same} same\n"

      return sb.toString()
   }

   private static void renderSemanticNode(SemanticNodeDiff node, int depth, boolean full, boolean color, StringBuilder sb, Map summary) {
      summary[node.status] = (summary[node.status] ?: 0) + 1

      def indent = '  ' * depth

      if (full || node.status != 'same') {
         sb << indent << line(node.status, "${node.templatePath}${nameLabel(node.name)} [${node.status}]", color) << "\n"

         node.fieldChanges.each {
            sb << indent << "    ${it.field}: ${fmt(it.oldValue)} -> ${fmt(it.newValue)}\n"
         }

         node.listChanges.each { lc ->
            def addedLabels = lc.added.collect { itemLabel(it, node.node2, lc.field) }.join(', ')
            def removedLabels = lc.removed.collect { itemLabel(it, node.node1, lc.field) }.join(', ')
            def modifiedLabels = lc.modified.collect { itemLabel(it.item, node.node2, lc.field) }.join(', ')
            sb << indent << "    ${lc.field}: +${lc.added.size()}[${addedLabels}] -${lc.removed.size()}[${removedLabels}] ~[${modifiedLabels}]\n"
            lc.modified.each { mi ->
               def changesStr = mi.changes.collect { "${it.field}: ${fmt(it.oldValue)} -> ${fmt(it.newValue)}" }.join(', ')
               sb << indent << "      ~ ${itemLabel(mi.item, node.node2, lc.field)}: ${changesStr}\n"
            }
         }
      }

      node.attributes.sort { it.key }.each { name, attrDiff ->
         if ((full || attrDiff.status != 'same') && attrDiff.fieldChanges) {
            sb << indent << "  " << line(attrDiff.status, "@${name} [${attrDiff.status}]", color) << "\n"
            attrDiff.fieldChanges.each {
               sb << indent << "    ${it.field}: ${fmt(it.oldValue)} -> ${fmt(it.newValue)}\n"
            }
         }

         attrDiff.children.each { child -> renderSemanticNode(child, depth + 1, full, color, sb, summary) }
      }
   }

   static Map semanticDiffToMap(SemanticOperationalTemplateDiff diff) {
      [
         templateMetadataChanges: diff.templateMetadataChanges.collect { fieldChangeToMap(it) },
         root: semanticNodeToMap(diff.root)
      ]
   }

   private static Map semanticNodeToMap(SemanticNodeDiff n) {
      [
         templatePath: n.templatePath, nodeId: n.nodeId, rmTypeName: n.rmTypeName,
         type: n.type, name: n.name, status: n.status,
         fieldChanges: n.fieldChanges.collect { fieldChangeToMap(it) },
         listChanges: n.listChanges.collect { listChangeToMap(it) },
         attributes: n.attributes.collectEntries { name, a -> [name, attributeDiffToMap(a)] }
      ]
   }

   private static Map attributeDiffToMap(AttributeDiff a) {
      [
         rmAttributeName: a.rmAttributeName, status: a.status,
         fieldChanges: a.fieldChanges.collect { fieldChangeToMap(it) },
         children: a.children.collect { semanticNodeToMap(it) }
      ]
   }

   private static Map listChangeToMap(ListChange lc) {
      [
         field: lc.field,
         added: lc.added,
         removed: lc.removed,
         modified: lc.modified.collect { [item: it.item, changes: it.changes.collect { fieldChangeToMap(it) }] }
      ]
   }

   private static Map fieldChangeToMap(FieldChange c) {
      [field: c.field, oldValue: c.oldValue, newValue: c.newValue]
   }

   // ---------------------------------------------------------------------
   // Structural (path-level) rendering
   // ---------------------------------------------------------------------

   static String renderStructural(OperationalTemplateDiff diff, boolean full, boolean color) {
      def sb = new StringBuilder()
      def summary = [added: 0, removed: 0, same: 0]

      sb << "Structural diff (path-level only - a node kept in place still shows 'same' even if its\n"
      sb << "internal constraints changed; use --mode semantic to see those)\n\n"

      def changeCache = [:]
      structuralHasChange(diff.root, changeCache)
      renderStructuralNode(diff.root, 0, full, color, sb, summary, changeCache)

      sb << "\n${summary.added} added, ${summary.removed} removed, ${summary.same} same\n"

      return sb.toString()
   }

   private static boolean structuralHasChange(NodeDiff node, Map changeCache) {
      boolean result = node.compareResult != 'same'
      node.attributeDiffs.each { name, children ->
         children.each { child ->
            if (structuralHasChange(child, changeCache)) result = true
         }
      }
      changeCache[node] = result
      return result
   }

   private static void renderStructuralNode(NodeDiff node, int depth, boolean full, boolean color, StringBuilder sb, Map summary, Map changeCache) {
      summary[node.compareResult] = (summary[node.compareResult] ?: 0) + 1

      if (full || changeCache[node]) {
         def indent = '  ' * depth
         sb << indent << line(node.compareResult, "${node.templateDataPath}${nameLabel(node.optNode?.text)} [${node.compareResult}]", color) << "\n"
      }

      node.attributeDiffs.sort { it.key }.each { name, children ->
         children.each { child -> renderStructuralNode(child, depth + 1, full, color, sb, summary, changeCache) }
      }
   }

   static Map structuralDiffToMap(OperationalTemplateDiff diff) {
      [root: structuralNodeToMap(diff.root)]
   }

   private static Map structuralNodeToMap(NodeDiff n) {
      [
         templateDataPath: n.templateDataPath,
         name: n.optNode?.text,
         compareResult: n.compareResult,
         attributeDiffs: n.attributeDiffs.collectEntries { name, children -> [name, children.collect { structuralNodeToMap(it) }] }
      ]
   }

   // ---------------------------------------------------------------------
   // shared helpers
   // ---------------------------------------------------------------------

   private static String line(String status, String text, boolean color) {
      def prefix = PREFIX[status] ?: '?'
      color ? "${COLOR[status]}${prefix} ${text}${RESET}" : "${prefix} ${text}"
   }

   private static String fmt(def v) {
      v == null ? 'null' : v.toString()
   }

   // node/archetype display name, as it appears in the OPT (e.g. ELEMENT.name/text, or an
   // archetype root's concept name) - quoted so it's visually distinct from the path segment.
   private static String nameLabel(def name) {
      name ? " '${name}'" : ''
   }

   // added/removed list items are plain strings (codeList) or CQuantityItem/CDvOrdinalItem
   // (quantity/ordinal lists) - reduce either to the label used to match it across sides.
   // For codeList (local at-codes, e.g. 'at0007'), ownerNode resolves the archetype's term
   // definition text for that code, e.g. "at0007 'code 2'" - ownerNode is node1 for a removed
   // item, node2 for an added/modified one, since the code only exists in the archetype
   // ontology on the side it's present on.
   private static String itemLabel(def item, def ownerNode = null, String field = null) {
      if (item == null) return 'null'
      if (item instanceof String) {
         if (field == 'codeList' && ownerNode) {
            def text = ownerNode.getOwnerArchetypeRoot()?.getText(item)
            if (text) return "${item} '${text}'"
         }
         return item
      }
      if (item.hasProperty('units') && item.units != null) return item.units.toString()
      if (item.hasProperty('value')) return item.value?.toString()
      return item.toString()
   }
}
