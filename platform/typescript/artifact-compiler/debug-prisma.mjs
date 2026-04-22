import { parsePrismaSchema } from './dist/extractors/prisma/schema-extractor.js';

const content = `model User {
  id    Int    @id @default(autoincrement())
  email String @unique
  posts Post[]
}`;

const models = parsePrismaSchema(content, 'test.prisma');
console.log('Models:', models.length);
for (const m of models) {
  console.log('Model:', m.name);
  console.log('  Fields:', m.fields.length, m.fields);
  console.log('  Relations:', m.relations.length, m.relations);
}
