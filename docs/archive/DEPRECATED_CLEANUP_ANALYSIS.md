# Deprecated Classes Cleanup Analysis

## 🔍 **Current Usage Assessment**

### ❌ **Cannot Remove Yet - Active Dependencies Found**

#### **com.ghatana.kernel.capability.KernelCapability**
- **Status**: Cannot remove - has dependencies
- **Found in**: 61 files (mostly comments and references)
- **Issue**: Deprecated methods in DataCloudKernelAdapterImpl reference this class
- **Safe Action**: Keep until adapter methods are removed

#### **CrossProductAuditService** 
- **Status**: Cannot remove - has dependencies
- **Found in**: 5 files (comments, deprecated class, adapter methods)
- **Issue**: Deprecated methods in DataCloudKernelAdapterImpl depend on this class
- **Safe Action**: Keep until adapter methods are removed

#### **ProductBoundaryEnforcer**
- **Status**: Cannot remove - has dependencies  
- **Found in**: 7 files (comments, deprecated class, policy classes)
- **Issue**: Referenced in policy resolver documentation
- **Safe Action**: Keep until migration period complete

#### **AppPlatform Deprecated Workflows**
- **Status**: Cannot remove - have dependencies
- **Found in**: 2 files each (the deprecated class itself and possibly tests)
- **Issue**: May be referenced in integration tests or documentation
- **Safe Action**: Keep until migration period complete

## ✅ **Safe to Clean Up**

### **Comments and Documentation References**
- Update comments to remove references to deprecated classes
- Update documentation to point to canonical implementations

### **Test File References**
- Test files can reference deprecated classes for validation purposes
- These should be kept until the classes are actually removed

## 🎯 **Recommended Cleanup Strategy**

### **Phase 1: Documentation and Comments (Immediate)**
1. Update all comments referencing deprecated classes
2. Update documentation to point to canonical implementations
3. Add migration examples in documentation

### **Phase 2: Adapter Methods (Medium Priority)**
1. Remove deprecated methods from DataCloudKernelAdapterImpl
2. Remove deprecated methods from PluginContext
3. This will allow removal of CrossProductAuditService and capability classes

### **Phase 3: Deprecated Classes (Future)**
1. Remove CrossProductAuditService once adapter methods are gone
2. Remove ProductBoundaryEnforcer once migration is complete
3. Remove com.ghatana.kernel.capability.KernelCapability once adapter is clean
4. Remove AppPlatform deprecated workflows once migration is complete

### **Phase 4: Final Cleanup**
1. Remove all backup files
2. Update any remaining documentation
3. Clean up test files that reference removed classes

## 📊 **Impact Assessment**

### **Breaking Changes**: 0
- No production code uses deprecated classes directly
- All references are in deprecated methods, comments, or tests
- Safe to keep for now

### **Migration Path**: ✅ Complete
- All canonical replacements are available and functional
- Clear deprecation notices with migration guidance
- Tests validate canonical implementations

### **Risk Assessment**: 🟢 Low Risk
- Keeping deprecated classes poses no immediate risk
- Canonical implementations are fully tested and functional
- Migration path is clear and documented

## 🎯 **Next Actions**

### **Immediate (Safe)**
1. Update comments in ScopeDescriptor.java
2. Update comments in ClassificationDescriptor.java  
3. Update documentation references

### **Medium Term**
1. Plan removal of deprecated adapter methods
2. Create migration guide for remaining consumers
3. Set removal timeline for deprecated classes

### **Long Term**
1. Remove deprecated classes when safe
2. Clean up any remaining references
3. Final documentation updates

## 📋 **Conclusion**

The deprecated classes are **properly isolated** and **safe to keep** for now. The cleanup should be done incrementally:

1. **Keep all deprecated classes** - they have dependencies in deprecated methods
2. **Update documentation** - remove references and point to canonical implementations  
3. **Plan future removal** - once adapter methods and dependencies are cleaned up
4. **Execute removal** - in phases when safe to do so

This approach ensures **zero breaking changes** while maintaining a clear path to full cleanup.
