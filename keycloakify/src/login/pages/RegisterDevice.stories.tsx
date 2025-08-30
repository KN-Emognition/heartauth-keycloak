// RegisterDevice.stories.tsx
import type { Meta, StoryObj } from "@storybook/react";
import { createKcPageStory } from "../KcPageStory";

const { KcPageStory } = createKcPageStory({ pageId: "registerDevice.ftl" });

export default {
  title: "login/registerDevice.ftl", 
  component: KcPageStory,
} satisfies Meta<typeof KcPageStory>;

type Story = StoryObj<typeof KcPageStory>;
export const Default: Story = { render: () => <KcPageStory /> };
