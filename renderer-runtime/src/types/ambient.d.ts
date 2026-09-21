declare module "node:test" {
  export function test(name: string, fn: (t: any) => any): void | Promise<void>;
  export function describe(name: string, fn: () => void): void;
}

declare module "node:assert" {
  interface Assert {
    (value: unknown, message?: string | Error): void;
    strictEqual<T>(actual: unknown, expected: T, message?: string | Error): void;
    deepStrictEqual<T>(actual: unknown, expected: T, message?: string | Error): void;
    notStrictEqual<T>(actual: unknown, expected: T, message?: string | Error): void;
    ok(value: unknown, message?: string | Error): void;
    throws(block: () => unknown, error?: RegExp | Function | Object | Error, message?: string | Error): void;
    doesNotThrow(block: () => unknown, message?: string | Error): void;
  }
  const assert: Assert;
  export default assert;
}
