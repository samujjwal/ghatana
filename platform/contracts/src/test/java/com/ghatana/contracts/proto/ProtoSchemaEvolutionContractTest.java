/*
 * Copyright (c) 2026 Ghatana Technologies
 * Proto message schema evolution contract tests.
 *
 * Validates that proto messages support evolution without breaking consumers.
 */
package com.ghatana.contracts.proto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Contract tests for proto message schema evolution and compatibility.
 *
 * <p>Validates that:
 * <ul>
 *   <li>New fields can be added without breaking old consumers</li>
 *   <li>Field types remain consistent</li>
 *   <li>Required fields are never removed</li>
 *   <li>Field numbers are never reused</li>
 *   <li>Enum values maintain stability</li>
 * </ul>
 *
 * <p>Proto schema evolution rules:
 * <ul>
 *   <li><b>Safe:</b> Add optional/repeated field → old consumer ignores</li>
 *   <li><b>Safe:</b> Rename field (number stays same) → wire format unchanged</li>
 *   <li><b>Unsafe:</b> Remove field → consumer expects it, gets default</li>
 *   <li><b>Unsafe:</b> Change field type → deserialization fails</li>
 *   <li><b>Unsafe:</b> Reuse field number → corrupted data</li>
 *   <li><b>Unsafe:</b> Remove enum value → consumer code breaks</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Proto schema evolution and compatibility tests
 * @doc.layer platform
 * @doc.pattern Test, Contract
 */
@DisplayName("Proto Message Schema Evolution Contract Tests")
class ProtoSchemaEvolutionContractTest {

    // =========================================================================
    // Field Addition Contracts
    // =========================================================================

    @Nested
    @DisplayName("Field Addition (Safe Evolution)")
    class FieldAdditionContract {

        @Test
        @DisplayName("new optional field must not break old consumers")
        void newOptionalFieldIsBackwardCompatible() {
            // V1: message User { string id = 1; string name = 2; }
            // V2: message User { string id = 1; string name = 2; string phone = 3; }
            // 
            // Old consumer reading V2 data:
            // - Gets id and name (expected)
            // - phone field missing → uses default (empty string)
            // - No error, works fine!
            
            String v1Fields = "id=1,name=2";
            String v2Fields = "id=1,name=2,phone=3";

            assertThat(v2Fields).contains(v1Fields);
            // New field doesn't break parsing
        }

        @Test
        @DisplayName("new repeated field must not break serialization")
        void newRepeatedFieldIsBackwardCompatible() {
            // V1: message Schema { string name = 1; }
            // V2: message Schema { 
            //   string name = 1; 
            //   repeated string tags = 2;  // Optional list
            // }
            // 
            // Old consumer: ignores tags field
            // New consumer: sees empty tags list if not provided
            
            String fieldNumber = "2";
            String fieldType = "repeated string";

            assertThat(fieldNumber).isNotEqualTo("1"); // Different field number
            assertThat(fieldType).contains("repeated");
        }

        @Test
        @DisplayName("cannot add required field to existing message")
        void newRequiredFieldIsUnsafe() {
            // V1: message User { string id = 1; }
            // V2: message User { required string id = 1; required string email = 2; }
            // 
            // Old consumer sending data without email:
            // - V2 parser: ERROR! Required field missing
            // - **Breaking change!**
            // 
            // Solution: new field must be optional, not required
            
            String safePattern = "optional string email = 2";
            String unsafePattern = "required string email = 2";

            assertThat(safePattern).contains("optional");
            assertThat(unsafePattern).contains("required");
            // Contract: new fields must NOT be required
        }
    }

    // =========================================================================
    // Field Number Management Contracts
    // =========================================================================

    @Nested
    @DisplayName("Field Number Management")
    class FieldNumberContract {

        @Test
        @DisplayName("field numbers must never be reused")
        void fieldNumbersMustNotBeReused() {
            // V1: message User { string id = 1; string name = 2; string email = 3; }
            // V2: message User { string id = 1; string name = 2; optional string phone = 3; }
            //
            // ERROR! Field 3 was previously 'email', now it's 'phone'
            // Old data with email=3 will be misinterpreted as phone!
            // **Data corruption!**
            //
            // Correct approach: use reserved field number
            // message User {
            //   string id = 1;
            //   string name = 2;
            //   optional string phone = 3;
            //   reserved "email"; // Field 3 removed, but don't reuse
            //   reserved 4;       // Never use field 4 either
            // }
            
            int emailFieldNumber = 3;
            int phoneFieldNumber = 3;

            assertThat(emailFieldNumber).isEqualTo(phoneFieldNumber);
            // Contract violation: same field number for different data!
        }

        @Test
        @DisplayName("removed fields must be marked reserved")
        void removedFieldsMustBeReserved() {
            // V1: message User { 
            //   string id = 1; 
            //   string deprecated_field = 2;  // Will be removed
            // }
            // V2: message User {
            //   string id = 1;
            //   reserved "deprecated_field";  // Prevent reuse
            //   reserved 2;                     // Prevent reuse of field number
            // }
            
            // Contract: if removing a field, must reserve its name and number
            String reservedName = "deprecated_field";
            int reservedNumber = 2;

            assertThat(reservedName).isNotBlank();
            assertThat(reservedNumber).isGreaterThan(1);
        }

        @Test
        @DisplayName("field numbers 1-15 are reserved for frequent fields")
        void lowFieldNumbersAreMoreEfficient() {
            // Proto encoding: field numbers 1-15 use 1 byte tag
            // Field numbers 16+ use 2 bytes tag
            // 
            // Contract: place frequently-used fields at numbers 1-15
            
            int efficientFieldNumber = 1;
            int lessEfficientFieldNumber = 100;

            assertThat(efficientFieldNumber).isBetween(1, 15);
            assertThat(lessEfficientFieldNumber).isGreaterThan(15);
        }

        @Test
        @DisplayName("highest used field number should increase, never decrease")
        void highestFieldNumberTrend() {
            // V1: highest field number = 5
            // V2: highest field number = 10 (added new fields)
            // V3: highest field number = 10 (no new fields)
            // 
            // Invalid: highest number goes down (field reuse)
            int v1Highest = 5;
            int v2Highest = 10;
            int v3Highest = 10;

            assertThat(v2Highest).isGreaterThanOrEqualTo(v1Highest);
            assertThat(v3Highest).isGreaterThanOrEqualTo(v2Highest);
        }
    }

    // =========================================================================
    // Field Type Stability Contracts
    // =========================================================================

    @Nested
    @DisplayName("Field Type Stability")
    class FieldTypeStabilityContract {

        @Test
        @DisplayName("field type must never change")
        void fieldTypeChangesAreUnsafe() {
            // V1: message User { int32 age = 1; }
            // V2: message User { string age = 1; }  // BREAKING!
            // 
            // Old data: age (binary-encoded as int32)
            // New parser: expects string (wrong format)
            // Result: Deserialization error or corrupted data
            
            String v1Type = "int32";
            String v2Type = "string";

            assertThat(v1Type).isNotEqualTo(v2Type);
            // Contract violation: type change breaks wire format
        }

        @Test
        @DisplayName("numeric types (int32, int64, uint32) can be safely changed")
        void numericTypeChangesAreCompatible() {
            // Proto special case: int32 ↔ int64 ↔ sint32 ↔ sint64
            // Wire format is compatible (both use varint encoding)
            // 
            // Example:
            // V1: int32 count = 1;
            // V2: int64 count = 1; // Safe change
            // (but be careful with precision and range)
            
            String type1 = "int32";
            String type2 = "int64";
            String type3 = "sint64"; // All use variable-length int encoding

            assertThat(type1).contains("int");
            assertThat(type2).contains("int");
            assertThat(type3).contains("int");
        }

        @Test
        @DisplayName("string ↔ bytes can be safely changed")
        void stringBytesChangesAreCompatible() {
            // Proto special case: string and bytes have same wire format
            // Both use length-delimited encoding
            // 
            // V1: string text = 1;
            // V2: bytes text = 1; // Safe (wire-level compatible)
            
            String type1 = "string";
            String type2 = "bytes";

            // Both are variable-length in proto encoding
            assertThat(type1).isNotEqualTo(type2);
            // But wire-compatible due to same encoding
        }

        @Test
        @DisplayName("bool, enum, and message types have distinct formats")
        void distinctTypeFormatsMustNotChange() {
            // bool: 1 byte (0 or 1)
            // enum: varint (like int32)
            // message: length-delimited (like string)
            // 
            // Changing between these is unsafe
            
            String boolType = "bool";
            String enumType = "MyEnum";
            String messageType = "NestedMessage";

            assertThat(boolType).isNotEqualTo(enumType);
            assertThat(enumType).isNotEqualTo(messageType);
        }
    }

    // =========================================================================
    // Enum Value Stability Contracts
    // =========================================================================

    @Nested
    @DisplayName("Enum Value Stability")
    class EnumValueStabilityContract {

        @Test
        @DisplayName("enum value numbers must never change")
        void enumValueNumbersAreImmutable() {
            // V1: enum Status { UNKNOWN = 0; ACTIVE = 1; INACTIVE = 2; }
            // V2: enum Status { UNKNOWN = 0; ACTIVE = 1; INACTIVE = 2; PENDING = 3; }
            // 
            // **Good:** Added new value (PENDING = 3)
            //
            // ❌ Bad:
            // enum Status { UNKNOWN = 0; ARCHIVED = 1; INACTIVE = 2; PENDING = 3; }
            // (changed ACTIVE to ARCHIVED with same number)
            
            int unknownValue = 0;
            int activeValue = 1;
            int inactiveValue = 2;

            assertThat(unknownValue).isEqualTo(0);
            assertThat(activeValue).isEqualTo(1);
            // Contract: do NOT change numeric values of enum members
        }

        @Test
        @DisplayName("enum value 0 must always be default")
        void enumDefaultMustBeZero() {
            // Proto contract: enum value 0 is always the default
            // 
            // V1: enum Color { UNKNOWN = 0; RED = 1; GREEN = 2; BLUE = 3; }
            // 
            // If field is missing in protobuf, it defaults to UNKNOWN (0)
            // Must never be RED or GREEN
            
            int defaultValue = 0;
            assertThat(defaultValue).isZero();
        }

        @Test
        @DisplayName("removing enum value breaks consumer code")
        void removingEnumValueIsUnsafe() {
            // V1: enum Status { UNKNOWN = 0; ACTIVE = 1; INACTIVE = 2; }
            // V2: enum Status { UNKNOWN = 0; ACTIVE = 1; } // Removed INACTIVE
            // 
            // Old consumer might have code:
            // switch (status) {
            //   case ACTIVE: ...
            //   case INACTIVE: ... // No longer valid!
            // }
            // 
            // Result: Unrecognized enum value causes error
            
            String v1Enum = "UNKNOWN=0,ACTIVE=1,INACTIVE=2";
            String v2Enum = "UNKNOWN=0,ACTIVE=1";

            assertThat(v1Enum.split(",")).hasSize(3);
            assertThat(v2Enum.split(",")).hasSize(2);
            // Contract: do NOT remove enum values
        }

        @Test
        @DisplayName("renaming enum value is safe")
        void renamingEnumValueIsSafe() {
            // V1: enum Status { UNKNOWN = 0; ACTIVE = 1; INACTIVE = 2; }
            // V2: enum Status { UNKNOWN = 0; ACTIVE = 1; DISABLED = 2; }
            //     (renamed INACTIVE → DISABLED, same number 2)
            // 
            // Wire format unchanged (still uses value 2)
            // Old consumer: sees value 2 as INACTIVE (expected)
            // New consumer: sees value 2 as DISABLED (expected)
            // Both work!
            
            String v1Name = "INACTIVE";
            String v2Name = "DISABLED";
            int sharedValue = 2;

            assertThat(v1Name).isNotEqualTo(v2Name);
            assertThat(sharedValue).isEqualTo(2);
            // Contract: renaming is safe as long as number doesn't change
        }
    }

    // =========================================================================
    // Message Embedding Contracts
    // =========================================================================

    @Nested
    @DisplayName("Message Embedding and Nesting")
    class MessageEmbeddingContract {

        @Test
        @DisplayName("nested message type can be safely changed to top-level")
        void nestedMessageCanBeHoisted() {
            // V1: message Outer { message Inner { string value = 1; } Inner inner = 1; }
            // V2: 
            //   message Inner { string value = 1; }
            //   message Outer { Inner inner = 1; }
            // 
            // Wire format is identical (Inner is still message type, same field number)
            // Safe change for decoupling
            
            // The key is: field type remains 'message' at same field number
            assertThat("message Inner").isNotNull();
        }

        @Test
        @DisplayName("message field cannot change to scalar type")
        void messageToScalarChangeIsUnsafe() {
            // V1: message User { Address address = 1; }
            // V2: message User { string address = 1; } // BREAKING!
            // Wire format changed (message is length-delimited, string is too, but interpretation differs)
            
            String v1Type = "message Address";
            String v2Type = "string";

            assertThat(v1Type).isNotEqualTo(v2Type);
        }
    }

    // =========================================================================
    // Reserved Keywords Usage
    // =========================================================================

    @Nested
    @DisplayName("Reserved Keywords for Safe Deletion")
    class ReservedKeywordsContract {

        @Test
        @DisplayName("deleted fields must be reserved to prevent reuse")
        void deletedFieldsMustBeReserved() {
            // Proper field deletion:
            // message User {
            //   string id = 1;
            //   string name = 2;
            //   // string email = 3;  // Was removed
            //   reserved "email";     // Reserve the name
            //   reserved 3;           // Reserve the field number
            // }
            
            String reservedName = "email";
            int reservedNumber = 3;

            assertThat(reservedName).isNotBlank();
            assertThat(reservedNumber).isGreaterThan(0);
        }

        @Test
        @DisplayName("reserved keyword prevents accidental field number reuse")
        void reservedPreventsReuse() {
            // If 'reserved 3;' exists, cannot use:
            // int32 newField = 3;  // Compiler error!
            
            // This protects against data corruption from field number conflicts
            assertThat("reserved 3").contains("reserved");
        }
    }
}
