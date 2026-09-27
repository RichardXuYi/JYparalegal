/**
 * Ambient declarations letting host-core/ sources compile against the shims:
 * the desktop code references the global Electron namespace and
 * process.resourcesPath, which only exist under real Electron typings.
 */
import type { ChildProcess } from 'node:child_process';
import type { MessageBoxOptions as ShimMessageBoxOptions, OpenDialogOptions as ShimOpenDialogOptions } from '../shims/electron';

declare global {
  namespace Electron {
    /** utilityProcess.fork is shimmed to child_process.fork. */
    type UtilityProcess = ChildProcess;
    type OpenDialogOptions = ShimOpenDialogOptions;
    type MessageBoxOptions = ShimMessageBoxOptions;
    /** Menus are no-ops on the server; keep the template shape permissive. */
    type MenuItemConstructorOptions = { [key: string]: unknown };
  }

  namespace NodeJS {
    interface Process {
      /** Never hit at runtime: guarded by app.isPackaged === false. */
      resourcesPath: string;
    }
  }
}

export {};
