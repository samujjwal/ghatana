import { graphql } from 'msw';

import { resolvers } from './resolvers';

export const handlers = [
  graphql.query('workspaces', (...args: unknown[]) => {
    const [req, res, ctx] = args;
    return res(ctx.data({ workspaces: resolvers.Query.workspaces() }));
  }),

  graphql.query('projects', (...args: unknown[]) => {
    const [req, res, ctx] = args;
    const { workspaceId } = req.variables as { workspaceId: string };
    return res(
      ctx.data({ projects: resolvers.Query.projects(null, { workspaceId }) })
    );
  }),

  graphql.query('projectKpis', (...args: unknown[]) => {
    const [req, res, ctx] = args;
    const { projectId } = req.variables as { projectId: string };
    return res(
      ctx.data({
        projectKpis: resolvers.Query.projectKpis(null, { projectId }),
      })
    );
  }),

  graphql.query('tasksForUser', (...args: unknown[]) => {
    const [req, res, ctx] = args;
    const { userId } = req.variables as { userId: string };
    return res(
      ctx.data({ tasksForUser: resolvers.Query.tasksForUser(null, { userId }) })
    );
  }),

  graphql.mutation('updateTaskStatus', (...args: unknown[]) => {
    const [req, res, ctx] = args;
    const { taskId, status } = req.variables as {
      taskId: string;
      status: string;
    };
    return res(
      ctx.data({
        updateTaskStatus: resolvers.Mutation.updateTaskStatus(null, {
          taskId,
          status,
        }),
      })
    );
  }),

  graphql.mutation('addComment', (...args: unknown[]) => {
    const [req, res, ctx] = args;
    const { taskId, content } = req.variables as {
      taskId: string;
      content: string;
    };
    return res(
      ctx.data({
        addComment: resolvers.Mutation.addComment(null, { taskId, content }),
      })
    );
  }),
];
