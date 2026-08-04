package com.cabolabs.openehr.opt.cli.services

import spock.lang.Specification
import com.cabolabs.openehr.opt.model.*
import com.cabolabs.openehr.opt.model.domain.*
import com.cabolabs.openehr.opt.model.primitive.*
import com.cabolabs.openehr.opt.diff.SemanticOperationalTemplateDiffAlgorithm

// Covers the primitive/constraint types the sample .opt fixtures don't happen to exercise as
// whole-subtree added nodes (CReal, CBoolean, CString with pattern+list, CDuration, CDate, CTime,
// ArchetypeSlot), plus an attribute-level existence change - making sure renderSemantic surfaces
// an actual value for every one of them, not just the generic 'added'/'modified' status line.
class OptDiffServiceRenderTest extends Specification {

   def "renders an added CReal range"() {
      given:
      def opt1 = optWith(new AttributeNode(rmAttributeName: 'a', type: 'C_SINGLE_ATTRIBUTE', children: []))
      def opt2 = optWith(new AttributeNode(rmAttributeName: 'a', type: 'C_SINGLE_ATTRIBUTE', children: [
         new PrimitiveObjectNode(templatePath: '/root/a', rmTypeName: 'DV_REAL', type: 'C_PRIMITIVE_OBJECT', text: 'Real',
            item: new CReal(range: new IntervalBigDecimal(lower: 0.0, upper: 100.0, lowerIncluded: true, upperIncluded: true)))
      ]))

      expect:
      render(opt1, opt2).contains('range: [0.0..100.0]')
   }

   def "renders an added CBoolean trueValid/falseValid"() {
      given:
      def opt1 = optWith(new AttributeNode(rmAttributeName: 'a', type: 'C_SINGLE_ATTRIBUTE', children: []))
      def opt2 = optWith(new AttributeNode(rmAttributeName: 'a', type: 'C_SINGLE_ATTRIBUTE', children: [
         new PrimitiveObjectNode(templatePath: '/root/a', rmTypeName: 'DV_BOOLEAN', type: 'C_PRIMITIVE_OBJECT', text: 'Bool',
            item: new CBoolean(trueValid: true, falseValid: false))
      ]))

      expect:
      def out = render(opt1, opt2)
      out.contains('trueValid: true')
      out.contains('falseValid: false')
   }

   def "renders an added CString pattern and list"() {
      given:
      def opt1 = optWith(new AttributeNode(rmAttributeName: 'a', type: 'C_SINGLE_ATTRIBUTE', children: []))
      def opt2 = optWith(new AttributeNode(rmAttributeName: 'a', type: 'C_SINGLE_ATTRIBUTE', children: [
         new PrimitiveObjectNode(templatePath: '/root/a', rmTypeName: 'DV_TEXT', type: 'C_PRIMITIVE_OBJECT', text: 'Str',
            item: new CString(pattern: '[A-Z]+', list: ['a', 'b']))
      ]))

      expect:
      def out = render(opt1, opt2)
      out.contains("pattern: [A-Z]+")
      out.contains('list: [a, b]')
   }

   def "renders an added CDuration pattern"() {
      given:
      def opt1 = optWith(new AttributeNode(rmAttributeName: 'a', type: 'C_SINGLE_ATTRIBUTE', children: []))
      def opt2 = optWith(new AttributeNode(rmAttributeName: 'a', type: 'C_SINGLE_ATTRIBUTE', children: [
         new PrimitiveObjectNode(templatePath: '/root/a', rmTypeName: 'DV_DURATION', type: 'C_PRIMITIVE_OBJECT', text: 'Dur',
            item: new CDuration(pattern: 'PYMWD'))
      ]))

      expect:
      render(opt1, opt2).contains('pattern: PYMWD')
   }

   def "renders an added CDate pattern"() {
      given:
      def opt1 = optWith(new AttributeNode(rmAttributeName: 'a', type: 'C_SINGLE_ATTRIBUTE', children: []))
      def opt2 = optWith(new AttributeNode(rmAttributeName: 'a', type: 'C_SINGLE_ATTRIBUTE', children: [
         new PrimitiveObjectNode(templatePath: '/root/a', rmTypeName: 'DV_DATE', type: 'C_PRIMITIVE_OBJECT', text: 'Date',
            item: new CDate(pattern: 'yyyy-mm-dd'))
      ]))

      expect:
      render(opt1, opt2).contains('pattern: yyyy-mm-dd')
   }

   def "renders an added CTime pattern"() {
      given:
      def opt1 = optWith(new AttributeNode(rmAttributeName: 'a', type: 'C_SINGLE_ATTRIBUTE', children: []))
      def opt2 = optWith(new AttributeNode(rmAttributeName: 'a', type: 'C_SINGLE_ATTRIBUTE', children: [
         new PrimitiveObjectNode(templatePath: '/root/a', rmTypeName: 'DV_TIME', type: 'C_PRIMITIVE_OBJECT', text: 'Time',
            item: new CTime(pattern: 'HH:MM:SS'))
      ]))

      expect:
      render(opt1, opt2).contains('pattern: HH:MM:SS')
   }

   def "renders an added ArchetypeSlot includes/excludes"() {
      given:
      def opt1 = optWith(new AttributeNode(rmAttributeName: 'a', type: 'C_SINGLE_ATTRIBUTE', children: []))
      def opt2 = optWith(new AttributeNode(rmAttributeName: 'a', type: 'C_SINGLE_ATTRIBUTE', children: [
         new ArchetypeSlot(templatePath: '/root/a', nodeId: 'at0099', rmTypeName: 'ELEMENT', type: 'ARCHETYPE_SLOT', text: 'Slot',
            includes: 'openEHR-EHR-.*\\.v1', excludes: 'openEHR-EHR-.*-legacy.*')
      ]))

      expect:
      def out = render(opt1, opt2)
      out.contains('includes: openEHR-EHR-.*\\.v1')
      out.contains('excludes: openEHR-EHR-.*-legacy.*')
   }

   def "renders an attribute-level existence change"() {
      given:
      def child1 = new ObjectNode(templatePath: '/root/a/child', nodeId: 'at0002', rmTypeName: 'ELEMENT', type: 'C_COMPLEX_OBJECT', text: 'Child')
      def child2 = new ObjectNode(templatePath: '/root/a/child', nodeId: 'at0002', rmTypeName: 'ELEMENT', type: 'C_COMPLEX_OBJECT', text: 'Child')

      def opt1 = optWith(new AttributeNode(
         rmAttributeName: 'a', type: 'C_SINGLE_ATTRIBUTE',
         existence: new IntervalInt(lower: 1, upper: 1, lowerIncluded: true, upperIncluded: true, lowerUnbounded: false, upperUnbounded: false),
         children: [child1]
      ))
      def opt2 = optWith(new AttributeNode(
         rmAttributeName: 'a', type: 'C_SINGLE_ATTRIBUTE',
         existence: new IntervalInt(lower: 0, upper: 1, lowerIncluded: true, upperIncluded: true, lowerUnbounded: false, upperUnbounded: false),
         children: [child2]
      ))

      expect:
      def out = render(opt1, opt2)
      out.contains('@a [modified]')
      out.contains('existence: [1..1] -> [0..1]')
   }

   private OperationalTemplate optWith(AttributeNode attr) {
      def root = new ObjectNode(templatePath: '/root', nodeId: 'at0001', rmTypeName: 'ELEMENT', type: 'C_COMPLEX_OBJECT', text: 'Root', attributes: [attr])
      new OperationalTemplate(templateId: 't1', definition: root)
   }

   private String render(OperationalTemplate opt1, OperationalTemplate opt2) {
      def diff = new SemanticOperationalTemplateDiffAlgorithm().diff(opt1, opt2)
      OptDiffService.renderSemantic(diff, true, false)
   }
}
