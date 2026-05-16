type FormDataConstructor = typeof FormData;

class MissingFormData {
  constructor() {
    throw new Error('FormData is not available in this runtime.');
  }
}

const FormDataImpl: FormDataConstructor =
  (globalThis.FormData as FormDataConstructor | undefined) ??
  (MissingFormData as unknown as FormDataConstructor);

export default FormDataImpl;
