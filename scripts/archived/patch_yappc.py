import re
path = '/Users/samujjwal/Development/ghatana/products/yappc/infrastructure/security/src/test/java/com/ghatana/yappc/infrastructure/security/SecurityScannerIntegrationTest.java'
with open(path, 'r') as f:
    text = f.read()

text = text.replace('import io.activej.test.rules.EventloopRule;', 'import com.ghatana.platform.testing.activej.EventloopTestBase;\nimport java.util.concurrent.Callable;')
text = text.replace('import org.junit.ClassRule;', '')
text = text.replace('import org.junit.DisplayName;', 'import org.junit.jupiter.api.DisplayName;')
text = text.replace('import org.junit.Test;', 'import org.junit.jupiter.api.Test;')
text = text.replace('public static EventloopRule eventloopRule = new EventloopRule();', '')
text = text.replace('@ClassRule\n', '')
text = text.replace('class SecurityScannerIntegrationTest {', 'class SecurityScannerIntegrationTest extends EventloopTestBase {')

text = text.replace('SecurityReport finding = new SecurityReport.Finding', 'SecurityReport.Finding finding = new SecurityReport.Finding')
text = text.replace('.getFindings()', '.findings()')
text = re.sub(r'result\.findingsByCategory\(([^)]+)\)', r'result.findings().stream().filter(f -> f.severity() == \1).toList()', text)

def replace_builder(match):
    block = match.group(0)
    scanner_name = re.search(r'\.scannerName\(([^)]+)\)', block).group(1)
    findings = re.search(r'\.findings\((.*?)\)(?:\n|\s)*\.build\(\)', block, re.DOTALL).group(1)
    if 'List.of()' in findings.replace(' ','').replace('\n',''):
        return f'SecurityReport.clean({scanner_name})'
    return f'SecurityReport.withFindings({findings}, {scanner_name})'

text = re.sub(r'SecurityReport\.builder\(\).*?\.build\(\)', replace_builder, text, flags=re.DOTALL)

with open(path, 'w') as f:
    f.write(text)
