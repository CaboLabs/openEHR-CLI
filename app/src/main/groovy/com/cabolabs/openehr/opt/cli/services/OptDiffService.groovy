package com.cabolabs.openehr.opt.cli.services

import com.cabolabs.openehr.opt.diff.SemanticOperationalTemplateDiff
import com.cabolabs.openehr.opt.diff.SemanticNodeDiff
import com.cabolabs.openehr.opt.diff.AttributeDiff
import com.cabolabs.openehr.opt.diff.FieldChange
import com.cabolabs.openehr.opt.diff.ListChange
import com.cabolabs.openehr.opt.diff.OperationalTemplateDiff
import com.cabolabs.openehr.opt.diff.NodeDiff
import com.cabolabs.openehr.opt.model.PrimitiveObjectNode
import com.cabolabs.openehr.opt.model.ArchetypeSlot
import com.cabolabs.openehr.opt.model.domain.CCodePhrase
import com.cabolabs.openehr.opt.model.domain.CDvQuantity
import com.cabolabs.openehr.opt.model.domain.CDvOrdinal
import com.cabolabs.openehr.opt.model.primitive.CInteger
import com.cabolabs.openehr.opt.model.primitive.CReal
import com.cabolabs.openehr.opt.model.primitive.CBoolean
import com.cabolabs.openehr.opt.model.primitive.CString
import com.cabolabs.openehr.opt.model.primitive.CDate
import com.cabolabs.openehr.opt.model.primitive.CDateTime
import com.cabolabs.openehr.opt.model.primitive.CTime
import com.cabolabs.openehr.opt.model.primitive.CDuration

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
      def ctx = [bcs: diff.breakingChanges, used: new HashSet()]
      renderSemanticNode(diff.root, 0, full, color, sb, summary, ctx)
      sb << "\n${summary.added} added, ${summary.removed} removed, ${summary.modified} modified, ${summary.same} same\n"

      // breaking changes with no field line to carry the tag (e.g. a removed attribute)
      def loose = diff.breakingChanges.findAll { !ctx.used.contains(it) }
      if (loose) {
         sb << "\nOther breaking changes:\n"
         loose.each { sb << "  ${it.templatePath} ${it.field ?: ''} [${breakingLabel(it)}] ${it.reason}\n" }
      }

      def confirmed = diff.breakingChanges.count { it.certain }
      def potential = diff.breakingChanges.size() - confirmed
      sb << "${confirmed} confirmed breaking, ${potential} potential breaking (potential ones need a check against existing data)\n"

      return sb.toString()
   }

   private static void renderSemanticNode(SemanticNodeDiff node, int depth, boolean full, boolean color, StringBuilder sb, Map summary, Map ctx) {
      summary[node.status] = (summary[node.status] ?: 0) + 1

      def indent = '  ' * depth

      if (full || node.status != 'same') {
         sb << indent << line(node.status, "${node.templatePath}${nameLabel(node.text)} [${node.status}]", color) << nodeTag(node, ctx) << "\n"

         node.fieldChanges.each {
            sb << indent << "    ${it.field}: ${fmt(it.oldValue)} -> ${fmt(it.newValue)}${tag(node.templatePath, it, ctx)}\n"
         }

         // whole-subtree added/removed nodes never run field comparison (nothing to diff
         // against), so fieldChanges/listChanges are always empty here - read the constraint
         // straight off the one-sided raw node instead, otherwise an added/removed node with
         // its own constraint (e.g. a new CInteger.list or CReal.range) shows no values at all.
         if (node.status == 'added' || node.status == 'removed') {
            def desc = describeConstraint(node.status == 'added' ? node.node2 : node.node1)
            if (desc) sb << indent << "    ${desc}\n"
         }

         node.listChanges.each { lc ->
            def addedLabels = lc.added.collect { itemLabel(it, node.node2, lc.field) }.join(', ')
            def removedLabels = lc.removed.collect { itemLabel(it, node.node1, lc.field) }.join(', ')
            def modifiedLabels = lc.modified.collect { itemLabel(it.item, node.node2, lc.field) }.join(', ')
            sb << indent << "    ${lc.field}: +${lc.added.size()}[${addedLabels}] -${lc.removed.size()}[${removedLabels}] ~[${modifiedLabels}]${tag(node.templatePath, new FieldChange(field: lc.field), ctx)}\n"
            lc.modified.each { mi ->
               def changesStr = mi.changes.collect { "${it.field}: ${fmt(it.oldValue)} -> ${fmt(it.newValue)}${tag(node.templatePath + '[units=' + mi.item + ']', it, ctx)}" }.join(', ')
               sb << indent << "      ~ ${itemLabel(mi.item, node.node2, lc.field)}: ${changesStr}\n"
            }
         }
      }

      node.attributes.sort { it.key }.each { name, attrDiff ->
         if ((full || attrDiff.status != 'same') && attrDiff.fieldChanges) {
            sb << indent << "  " << line(attrDiff.status, "@${name} [${attrDiff.status}]", color) << "\n"
            attrDiff.fieldChanges.each {
               sb << indent << "    ${it.field}: ${fmt(it.oldValue)} -> ${fmt(it.newValue)}${tag(node.templatePath, it, ctx)}\n"
            }
         }

         attrDiff.children.each { child -> renderSemanticNode(child, depth + 1, full, color, sb, summary, ctx) }
      }
   }

   static Map semanticDiffToMap(SemanticOperationalTemplateDiff diff) {
      [
         templateMetadataChanges: diff.templateMetadataChanges.collect { fieldChangeToMap(it) },
         breakingChanges: diff.breakingChanges.collect { [
            templatePath: it.templatePath, category: it.category, field: it.field,
            oldValue: it.oldValue, newValue: it.newValue, reason: it.reason,
            certain: it.certain, level: it.certain ? 'confirmed' : 'potential'
         ] },
         root: semanticNodeToMap(diff.root)
      ]
   }

   private static Map semanticNodeToMap(SemanticNodeDiff n) {
      [
         templatePath: n.templatePath, nodeId: n.nodeId, rmTypeName: n.rmTypeName,
         type: n.type, text: n.text, status: n.status,
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

   // no value means no constraint, shown as a hyphen instead of a technical null
   private static String fmt(def v) {
      v == null ? '-' : v.toString()
   }

   private static String breakingLabel(def bc) {
      bc.certain ? 'CONFIRMED BREAKING' : 'POTENTIAL BREAKING'
   }

   // finds the breaking change for a field change of the node/item at path, marks it as shown
   private static String tag(String path, FieldChange c, Map ctx) {
      def bc = findBreaking(path, c, ctx)
      return bc ? "  [${breakingLabel(bc)}]" : ''
   }

   private static def findBreaking(String path, FieldChange c, Map ctx) {
      def bc = ctx.bcs.find { it.templatePath == path && it.field == c.field &&
                              (c.newValue == null || it.newValue?.toString() == c.newValue.toString() || it.category == 'value_removed') }
      if (bc) ctx.used << bc
      return bc
   }

   // node-level breaking changes: a new mandatory node, a removed node
   private static String nodeTag(SemanticNodeDiff node, Map ctx) {
      def bcs = ctx.bcs.findAll { it.templatePath == node.templatePath && it.category in ['node_added_mandatory', 'node_removed'] }
      bcs.each { ctx.used << it }
      return bcs ? bcs.collect { "  [${breakingLabel(it)}]" }.join('') : ''
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
      if (item == null) return '-'
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

   // describes the own constraint carried by a whole-subtree added/removed ObjectNode - mirrors
   // SemanticOperationalTemplateDiffAlgorithm's type-specific comparators, but one-sided (no
   // diffing, just reading what's there) since an added/removed node has nothing on the other
   // side to compare against.
   private static String describeConstraint(def n) {
      if (n == null) return null

      if (n instanceof CCodePhrase) {
         def parts = []
         if (n.terminologyId) parts << "terminologyId: ${n.terminologyId}"
         if (n.codeList) parts << "codeList: [${n.codeList.collect { itemLabel(it, n, 'codeList') }.join(', ')}]"
         return parts ? parts.join(', ') : null
      }
      if (n instanceof CDvQuantity) {
         return n.list ? "list: [${n.list.collect { it.units }.join(', ')}]" : null
      }
      if (n instanceof CDvOrdinal) {
         return n.list ? "list: [${n.list.collect { it.value }.join(', ')}]" : null
      }
      if (n instanceof ArchetypeSlot) {
         def parts = []
         if (n.includes) parts << "includes: ${n.includes}"
         if (n.excludes) parts << "excludes: ${n.excludes}"
         return parts ? parts.join(', ') : null
      }
      if (n instanceof PrimitiveObjectNode) {
         return describePrimitiveItem(n.item)
      }
      return null
   }

   private static String describePrimitiveItem(def item) {
      if (item == null) return null

      def parts = []
      if (item instanceof CInteger) {
         if (item.range) parts << "range: ${intervalStr(item.range)}"
         if (item.list)  parts << "list: ${item.list}"
      }
      else if (item instanceof CReal) {
         if (item.range) parts << "range: ${intervalStr(item.range)}"
      }
      else if (item instanceof CBoolean) {
         if (item.trueValid != null)  parts << "trueValid: ${item.trueValid}"
         if (item.falseValid != null) parts << "falseValid: ${item.falseValid}"
      }
      else if (item instanceof CString) {
         if (item.pattern) parts << "pattern: ${item.pattern}"
         if (item.list)    parts << "list: ${item.list}"
      }
      else if (item instanceof CDuration) {
         if (item.pattern) parts << "pattern: ${item.pattern}"
         if (item.range)   parts << "range: ${intervalStr(item.range)}"
      }
      else if (item instanceof CDate || item instanceof CDateTime || item instanceof CTime) {
         if (item.pattern) parts << "pattern: ${item.pattern}"
      }

      return parts ? parts.join(', ') : null
   }

   // works for IntervalInt, IntervalBigDecimal and IntervalDuration alike (duck typing on the
   // shared lowerIncluded/upperIncluded/lowerUnbounded/upperUnbounded/lower/upper fields) - same
   // approach as the SDK algorithm's own intervalStr, kept local since that one's private.
   private static String intervalStr(def interval) {
      if (interval == null) return null

      def lo = interval.lowerUnbounded ? '*' : interval.lower
      def hi = interval.upperUnbounded ? '*' : interval.upper
      def lb = interval.lowerIncluded ? '[' : '('
      def ub = interval.upperIncluded ? ']' : ')'

      return "${lb}${lo}..${hi}${ub}"
   }
}
