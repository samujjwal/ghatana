import type {
  ComponentData,
  ButtonData,
  CardData,
  TextFieldData,
  TypographyData,
  BoxData,
} from './schemas';

/**
 * Convert component data to JSX string
 */
function componentToJSX(data: ComponentData, indent: number = 0): string {
  const indentStr = '  '.repeat(indent);

  switch (data.type) {
    case 'button': {
      const buttonData = data as ButtonData;
      const props: string[] = [];
      
      if (buttonData.variant !== 'contained') props.push(`variant="${buttonData.variant}"`);
      if (buttonData.color !== 'primary') props.push(`color="${buttonData.color}"`);
      if (buttonData.size !== 'medium') props.push(`size="${buttonData.size}"`);
      if (buttonData.disabled) props.push('disabled');
      if (buttonData.fullWidth) props.push('fullWidth');

      const propsStr = props.length > 0 ? ` ${  props.join(' ')}` : '';
      return `${indentStr}<Button${propsStr}>\n${indentStr}  ${buttonData.text}\n${indentStr}</Button>`;
    }

    case 'card': {
      const cardData = data as CardData;
      const props: string[] = [];
      
      if (cardData.elevation !== 2) props.push(`elevation={${cardData.elevation}}`);

      const propsStr = props.length > 0 ? ` ${  props.join(' ')}` : '';
      let jsx = `${indentStr}<Card${propsStr}>\n`;

      if (cardData.title) {
        jsx += `${indentStr}  <CardHeader\n`;
        jsx += `${indentStr}    title="${cardData.title}"\n`;
        if (cardData.subtitle) {
          jsx += `${indentStr}    subheader="${cardData.subtitle}"\n`;
        }
        jsx += `${indentStr}  />\n`;
      }

      if (cardData.content) {
        jsx += `${indentStr}  <CardContent>\n`;
        jsx += `${indentStr}    <Typography>${cardData.content}</Typography>\n`;
        jsx += `${indentStr}  </CardContent>\n`;
      }

      jsx += `${indentStr}</Card>`;
      return jsx;
    }

    case 'textfield': {
      const textFieldData = data as TextFieldData;
      const props: string[] = [];
      
      props.push(`label="${textFieldData.label}"`);
      if (textFieldData.placeholder) props.push(`placeholder="${textFieldData.placeholder}"`);
      if (textFieldData.variant !== 'outlined') props.push(`variant="${textFieldData.variant}"`);
      if (textFieldData.size !== 'medium') props.push(`size="${textFieldData.size}"`);
      if (textFieldData.required) props.push('required');
      if (textFieldData.disabled) props.push('disabled');
      if (textFieldData.fullWidth) props.push('fullWidth');
      if (textFieldData.multiline) {
        props.push('multiline');
        if (textFieldData.rows !== 1) props.push(`rows={${textFieldData.rows}}`);
      }

      const propsStr = props.join(' ');
      return `${indentStr}<TextField ${propsStr} />`;
    }

    case 'typography': {
      const typographyData = data as TypographyData;
      const props: string[] = [];
      
      if (typographyData.variant !== 'body1') props.push(`variant="${typographyData.variant}"`);
      if (typographyData.color) props.push(`color="${typographyData.color}"`);
      if (typographyData.align !== 'left') props.push(`align="${typographyData.align}"`);

      const propsStr = props.length > 0 ? ` ${  props.join(' ')}` : '';
      return `${indentStr}<Typography${propsStr}>\n${indentStr}  ${typographyData.text}\n${indentStr}</Typography>`;
    }

    case 'box': {
      const boxData = data as BoxData;
      const twClasses: string[] = [];
      
      if (boxData.padding !== 2) twClasses.push(`p-${boxData.padding * 2}`);
      if (boxData.margin !== 0) twClasses.push(`m-${boxData.margin * 2}`);
      if (boxData.backgroundColor) twClasses.push(`bg-[${boxData.backgroundColor}]`);
      if (boxData.borderRadius !== 0) twClasses.push(boxData.borderRadius >= 2 ? 'rounded-lg' : 'rounded');
      if (boxData.display !== 'block') {
        const displayMap: Record<string, string> = { flex: 'flex', grid: 'grid', 'inline-flex': 'inline-flex', none: 'hidden' };
        twClasses.push(displayMap[boxData.display] || boxData.display);
      }
      if (boxData.flexDirection) {
        const dirMap: Record<string, string> = { column: 'flex-col', row: 'flex-row', 'column-reverse': 'flex-col-reverse', 'row-reverse': 'flex-row-reverse' };
        twClasses.push(dirMap[boxData.flexDirection] || '');
      }
      if (boxData.justifyContent) {
        const justifyMap: Record<string, string> = { center: 'justify-center', 'flex-start': 'justify-start', 'flex-end': 'justify-end', 'space-between': 'justify-between', 'space-around': 'justify-around' };
        twClasses.push(justifyMap[boxData.justifyContent] || '');
      }
      if (boxData.alignItems) {
        const alignMap: Record<string, string> = { center: 'items-center', 'flex-start': 'items-start', 'flex-end': 'items-end', stretch: 'items-stretch', baseline: 'items-baseline' };
        twClasses.push(alignMap[boxData.alignItems] || '');
      }

      const classStr = twClasses.filter(Boolean).length > 0 ? ` className="${twClasses.filter(Boolean).join(' ')}"` : '';
      return `${indentStr}<Box${classStr}>\n${indentStr}  {/* Container content */}\n${indentStr}</Box>`;
    }

    default:
      return `${indentStr}<!-- Unknown component: ${data.type} -->`;
  }
}

/**
 * Export components to complete JSX file
 */
export function exportToJSX(components: ComponentData[], componentName: string = 'MyPage'): string {
  const imports = new Set<string>(['React']);
  const muiImports = new Set<string>();

  // Collect imports based on component types
  components.forEach((comp) => {
    switch (comp.type) {
      case 'button':
        muiImports.add('Button');
        break;
      case 'card':
        muiImports.add('Card');
        muiImports.add('CardHeader');
        muiImports.add('CardContent');
        muiImports.add('Typography');
        break;
      case 'textfield':
        muiImports.add('TextField');
        break;
      case 'typography':
        muiImports.add('Typography');
        break;
      case 'box':
        muiImports.add('Box');
        break;
    }
  });

  let jsx = '';

  // Add imports
  jsx += `import React from 'react';\n`;
  if (muiImports.size > 0) {
    jsx += `import { ${Array.from(muiImports).sort().join(', ')} } from '@ghatana/ui';\n`;
  }
  jsx += '\n';

  // Add component
  jsx += `export const ${componentName}: React.FC = () => {\n`;
  jsx += '  return (\n';
  
  if (components.length === 1) {
    jsx += `${componentToJSX(components[0], 2)  }\n`;
  } else {
    jsx += '    <>\n';
    components.forEach((comp) => {
      jsx += `${componentToJSX(comp, 3)  }\n`;
    });
    jsx += '    </>\n';
  }
  
  jsx += '  );\n';
  jsx += '};\n';
  jsx += '\n';
  jsx += `export default ${componentName};\n`;

  return jsx;
}

/**
 * Download JSX as file
 */
export function downloadJSX(components: ComponentData[], filename: string = 'MyPage.tsx'): void {
  const jsx = exportToJSX(components, filename.replace('.tsx', ''));
  const blob = new Blob([jsx], { type: 'text/plain' });
  const url = URL.createObjectURL(blob);
  
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  
  URL.revokeObjectURL(url);
}
